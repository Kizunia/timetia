# Master prompt: convert the core modules to raw JDBC

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** TBD
- **Last updated:** 2026-11-16

Paste everything below the line into a new Claude Code session opened in the repository root (`D:\dbms-oop-proj`). The plan it follows is [jdbc-persistence-plan.md](jdbc-persistence-plan.md).

---

You are helping me with my college course project. It is graded under two subjects: **Database Management Systems (SE2004)** and **Object Oriented Programming in Java (SE2006)**. The project is **Timetia**, a lecture-rescheduling and timetable system. The backend (`backend/`) is Spring Boot 4 on Java 25 with MySQL 8.4 and Flyway, and it currently persists through JPA/Hibernate. The courses want real SQL and plain Java, so we are moving the core modules to hand-written SQL over JDBC.

## Read first, in this order

1. `timetia-docs/engineering/jdbc-persistence-plan.md` — the plan. It is the source of truth for scope, rules and phases.
2. `SY-AY-2026-27-Sem-I-Structure-Syllabus.pdf` — the syllabus (use `pdftotext -layout`). DBMS is SE2004, OOP is SE2006.
3. `kibootia-docs/decisions/0003-hexagonal-architecture-where-boundaries-matter.md` and `0011-mysql-jpa-hibernate-persistence.md`.
4. For the module you are converting: its port interface in `application/`, its `Jpa*Store` in `persistence/`, its Flyway migrations in `backend/src/main/resources/db/migration/<module>/`, and its integration tests under `backend/src/test/`.
5. `access/persistence/JpaRoleStore.java` — an existing example of `JdbcClient` used beside JPA in one transaction.

## What to do

Replace the JPA adapters of these modules with JDBC adapters, one phase at a time, in the order the plan lists: `JdbcSupport` base class, then `notifications`, `ClaimStore`, `TimetableStore` and `OccurrenceStore`, `TicketStore`, then the `reference` stores. Leave `access`, `identity`, `audit`, `files`, `notes`, `platform`, `academic` and `issues` on JPA.

## Hard rules

- **Keep the port interfaces unchanged.** Services, controllers and security must not change. Only the adapter in `persistence/` changes.
- **Plain JDBC only:** `DataSource`, `Connection`, `PreparedStatement`, `ResultSet`. No `JdbcClient`, `JdbcTemplate`, MyBatis, jOOQ or other libraries in the converted adapters. Join the service's Spring transaction with `DataSourceUtils.getConnection` / `releaseConnection`, and prove it with a test before relying on it.
- **Reproduce what JPA did** — the table in section 5 of the plan: tenant filter on every query, optimistic-lock version check, audit columns, UUID as `BINARY(16)`, soft delete, duplicate key to `DataIntegrityViolationException`, paging with a count query. Find the exact exception types the JPA adapters throw and throw the same ones.
- **One table, one technology.** When a module is converted, delete its JPA entities, repositories and entity mappers. Convert all stores of a module together.
- **Flyway owns the schema.** Add a migration if the schema must change; never alter tables by hand.
- **Never edit an existing test to make it pass.** If a test fails, the adapter is wrong or the test reveals a real difference; tell me which.
- Use `?` placeholders for every value. Never build SQL by concatenating input.
- Keep ArchUnit and Spring Modulith tests green.

## Style

Write the code plainly, the way a student who understands JDBC would: one SQL text block per query with a short comment saying what it answers, clear names, try-with-resources, specific exceptions with the cause attached. Do not add clever abstractions or comment noise. Match the surrounding code's idiom.

## Project facts already decided

- It is a **group project**; I am one member. Docker is available. `issues` stays on JPA and is out of scope. There is **no console app**: the REST API and its tests are the demo.

## The 20 lab queries are mine

The DBMS lab requires each student to write at least 20 SQL queries independently, so each group member has their own file, `database/04_queries_<name>.sql`. **Do not write the contents of my file.** You may create it with a header, explain a query I show you, review it, and help me find a bug in it. You may write the adapters, the `database/` DDL/seed/view/index/DCL/TCL scaffolding, and the tests. Tell me which of my queries cover which syllabus topic (joins, subqueries, aggregates, `HAVING`, correlated subqueries) so I can see what is missing.

## How to work

1. **Phase 0 first:** check that Docker is running, run `./mvnw verify` (in `backend/`) on the untouched branch, and report the result. Do not start converting until you know the baseline. If it is red, tell me what fails.
2. Work on current branch only.
3. At the start of each phase, tell me in a few lines what you will change. After it, run that module's tests and the architecture tests, and report pass/fail and the count honestly. Do not claim a phase is done on a test run you did not do.
4. One phase per turn. Stop and let me review before the next.
5. If the plan and the code disagree, say so and ask; do not guess.

## Done means

- Every converted module's adapters are JDBC, with no leftover JPA entity for the tables they own.
- `./mvnw verify` passes with the same tests as the baseline, none edited or skipped.
- `database/` holds the schema, seed, views/indexes, DCL and TCL scripts, and each member's own `04_queries_<name>.sql`.
- A short note in `timetia-docs/engineering/` records what was converted, what was not, and the verified test result.
