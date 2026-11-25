# Plan: raw JDBC for the core modules

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** TBD
- **Last updated:** 2026-11-16

Timetia is also the course project for **SE2004 Database Management Systems** and **SE2006 Object Oriented Programming** (SY Sem-I 2026-27). The backend persists through JPA/Hibernate, which writes the SQL at runtime and hides it. The courses want plain Java and plain SQL. This plan moves the **modules that carry the project** to hand-written SQL over JDBC and leaves the rest alone.

## 1. Decision

- The modules in tier 1 and tier 2 (section 2) get a **JDBC adapter** written with `Connection`, `PreparedStatement` and `ResultSet`. Every query is a SQL string in the source.
- The **port interfaces stay** (`TicketStore`, `ClaimStore`, ...). They already are the DAO interfaces of the DAO pattern: use cases depend on the interface, one class implements it ([ADR 0003](../../kibootia-docs/decisions/0003-hexagonal-architecture-where-boundaries-matter.md)). Only the adapter changes, so services, controllers and security do not.
- Everything else stays on JPA. It is Kibootia plumbing, not what the course marks.
- Nothing in the syllabus asks for stored procedures, triggers or a mapping library, so none are used.

## 2. What moves and what stays

| Tier | Module | Port(s) | Why |
| --- | --- | --- | --- |
| 1 | `timetable` | `TimetableStore`, `OccurrenceStore`, `ClaimStore` | The database does the hard part here: a unique key on `occurrence_claim` is what stops two changes taking one slot. Joins, `FOR UPDATE` locks, range queries. |
| 1 | `rescheduling` | `TicketStore` | The ticket lifecycle: tickets, changes, reviews, conflict checks. Transactions, dynamic search, subqueries. |
| 2 | `reference` | `CampusStore`, `CatalogStore`, `TeachingAssignmentStore` | The master data. Most of the join, self-join and aggregate queries the DBMS lab asks for come from these tables. |
| 2 | `notifications` | `NotificationStore` | Small and simple. A quick win and a good first adapter to learn the pattern on if tier 1 feels big. |
| stays JPA | `access`, `identity`, `audit`, `files`, `notes`, `platform`, `academic`, `issues` | | Foundation modules from Kibootia, plus `issues`, which is out of scope ([section 9](#9-decisions-taken)). |

About 3,600 lines of JPA persistence code are replaced for tiers 1 and 2: timetable 1,010, rescheduling 1,080, reference 1,330, notifications 190.

## 3. Syllabus coverage

| Syllabus item | Where it shows up |
| --- | --- |
| DBMS: DDL, constraints, DML (practical 4) | `database/01_schema.sql`, `02_seed.sql`, taken from the Flyway migrations |
| DBMS: DQL, aggregates, `GROUP BY`, `HAVING` (5) | queries inside the adapters, and each member's `database/04_queries_<name>.sql` |
| DBMS: inner, outer and self joins, nested and correlated subqueries (6) | teaching assignments, live timetable, clash and conflict queries |
| DBMS: views, indexes, `GRANT`/`REVOKE` (7) | `database/05_views_indexes.sql`, `06_dcl.sql` |
| DBMS: `COMMIT`, `ROLLBACK`, `SAVEPOINT` (8) | `database/07_tcl.sql`, and JDBC transactions in the ticket adapter |
| DBMS: at least 20 queries written by each student (8) | `database/04_queries_<name>.sql`, one file per member, written by that member (section 8) |
| OOP: interfaces, abstraction, polymorphism | the port interface and its JDBC implementation; an abstract `JdbcSupport` base class |
| OOP: packages, access protection | each adapter is package-private inside its module's `persistence` package |
| OOP: user-defined and chained exceptions | SQL errors are caught and rethrown as domain exceptions with the cause attached |
| OOP: file handling | export of timetable and ticket reports to a text or CSV file |

## 4. Shape of a JDBC adapter

```
<module>/persistence/
  JdbcTicketStore.java      implements TicketStore, SQL strings and row mapping
  (JPA entities, repositories, entity mappers for this module are deleted)
platform/persistence/
  JdbcSupport.java          abstract base: connection, UUID <-> BINARY(16), exception translation
```

- One adapter implements one port and is package-private. Row mapping is a private method in the adapter.
- `JdbcSupport` is small and is written once. It holds the three things every adapter repeats: getting the connection, converting a `UUID` to and from `BINARY(16)`, and turning an `SQLException` into the exception the rest of the code expects.
- SQL stays inside the adapter as text blocks, one constant per query, so it can be read top to bottom.

## 5. What JPA did for free that the adapters must now do

The JPA entities carry behaviour that is easy to miss. Each JDBC adapter must reproduce all of it, or the existing API tests will fail.

| JPA did this | The adapter must |
| --- | --- |
| Hibernate `@TenantId` adds the tenant to every query | put `tenant_id = ?` in every `WHERE`, taking the value from `TenantContext.require()`, and write it on every `INSERT` |
| `@Version` optimistic locking | `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`; if 0 rows change, throw the same optimistic-locking exception the JPA adapter threw (the API turns it into 409) |
| Spring Data auditing fills `created_at/by`, `updated_at/by` | set the four columns on insert and the two `updated_*` columns on update |
| UUIDv7 ids generated in Java | keep generating them in Java; bind as 16 bytes |
| Soft delete through `record_status` | filter `record_status` in reads; set it instead of `DELETE` where the JPA adapter did |
| A duplicate key raises a data-integrity violation | catch MySQL error 1062 and throw `DataIntegrityViolationException`, which is what `ClaimStore` documents and the use cases expect |
| Hibernate shares the use case's transaction | take the connection with `DataSourceUtils.getConnection(dataSource)` and release it with `DataSourceUtils.releaseConnection`, so the adapter joins the `@Transactional` of the service. Do not call `dataSource.getConnection()` directly. `JpaRoleStore` already mixes `JdbcClient` with JPA this way. **Verify with a test early.** |
| `Page`/`Pageable` | run a `LIMIT ? OFFSET ?` query and a matching `COUNT(*)` query |

## 6. Rules that keep the conversion safe

1. **One table, one technology.** Do not leave a JPA entity mapped to a table that a JDBC adapter writes: Hibernate's cache would go stale. Convert every store of a module together and delete that module's entities and repositories.
2. **Flyway stays the owner of the schema.** Do not hand-edit tables outside migrations. The `database/` scripts are generated from, or kept in step with, the migrations.
3. **The existing integration tests are the acceptance tests.** The API and domain tests must pass unchanged. Do not edit a test to make a conversion pass.
4. **ArchUnit and Modulith checks stay green.** Adapters live in `persistence`, the application layer never imports them.
5. **No new libraries.** `java.sql` and the Spring transaction helpers already on the classpath.

## 7. Phases

Each phase ends with the tests of that module passing.

| # | Phase | Notes |
| --- | --- | --- |
| 0 | Baseline | Start Docker, run `./mvnw verify` on `om/api` untouched and record the result. The Testcontainers tests need Docker. Nothing below is trustworthy without this. |
| 1 | `JdbcSupport` and a transaction-joining test | Prove the adapter shares the service transaction and that rollback undoes its writes. |
| 2 | `notifications` | Smallest module; learn the pattern. Skip to 3 if you would rather start on the core. |
| 3 | `ClaimStore` | Small, and the unique-key behaviour is the heart of the project. |
| 4 | `TimetableStore`, `OccurrenceStore` | Includes `SELECT ... FOR UPDATE` for `lockAll` and the live-timetable queries. |
| 5 | `TicketStore` | Largest. Do the reads first, then writes, then the search query. |
| 6 | `reference` stores | Three ports, many tables; mostly straightforward CRUD plus the join-heavy lookups. |
| 7 | Cleanup | Delete the dead JPA entities, repositories and mappers of converted modules; confirm `ddl-auto=validate` and the Modulith docs still pass. |
| 8 | SQL lab scripts and report export | `database/` files from section 3, and the file-handling export. |

## 8. Authorship and the lab queries

The DBMS lab states that each student writes and runs at least 20 SQL queries independently. The project is a group, so **each member writes their own set of at least 20**, kept as `database/04_queries_<name>.sql`. An assistant may explain a query, review it, or find the bug in it, and may scaffold the adapters, but should not write the submitted queries. Check the college's policy on AI assistance and disclose where it asks.

## 9. Decisions taken

- **Docker is available**, so the Testcontainers tests can run.
- **Group project.** The DBMS mini-project is a group activity; the adapters can be split between members by module (section 7), and each member writes their own lab queries (section 8).
- **`issues` is out of scope** and stays on JPA.
- **No console application.** The REST API and its existing tests are the demo.
