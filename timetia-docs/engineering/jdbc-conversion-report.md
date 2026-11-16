# Report: the JDBC conversion

- **Type:** report
- **Module:** platform
- **Status:** draft (the full test run is still to be done, see section 4)
- **Owner:** TBD
- **Last updated:** 2026-11-16

What was converted from JPA to hand-written JDBC, what was not, what was added, and what has been verified. The plan is
[jdbc-persistence-plan.md](jdbc-persistence-plan.md).

## 1. Converted

| Module | Adapters now | JPA classes deleted |
| --- | --- | --- |
| `notifications` | `JdbcNotificationStore` | entity, repository, store |
| `timetable` | `JdbcClaimStore`, `JdbcTimetableStore`, `JdbcOccurrenceStore` | 3 entities, 3 repositories, specifications, mapper, 3 stores |
| `rescheduling` | `JdbcTicketStore` | 5 entities, 5 repositories, specifications, mapper, store |
| `reference` | `JdbcCampusStore`, `JdbcCatalogStore`, `JdbcTeachingAssignmentStore`, `JdbcScopeDirectory`, base class `JdbcReferenceSupport` | 8 entities, 8 repositories, 2 mappers, 4 stores |

Port interfaces, services, controllers and security are unchanged. No converted module imports `jakarta.persistence`,
Hibernate or Spring Data JPA, and none uses `JdbcClient`, `JdbcTemplate` or another library; the adapters use `DataSource`,
`Connection`, `PreparedStatement` and `ResultSet`. Every value is a `?` placeholder. The only text put into SQL is the table name
(always a constant of the adapter), the `ORDER BY` columns (from a whitelist in the adapter) and the number of `?` in an `IN`
list.

`JdbcScopeDirectory` (was `JpaScopeDirectory`) is not one of the three ports the plan names, but it read the same repositories,
so it had to move with them (rule: one table, one technology).

**Not converted, as decided:** `access`, `identity`, `audit`, `files`, `notes`, `platform`, `academic`, `issues`.

## 2. Added

- `platform/persistence/JdbcSupport`: the abstract base of every adapter. It takes the connection with `DataSourceUtils` (so a
  use case's `@Transactional` covers the adapter), binds `UUID`, `Instant` and enums, reads rows, supplies tenant, actor, time
  and a monotonic UUIDv7, and translates `SQLException`: integrity errors to `DataIntegrityViolationException`, deadlock and
  lock timeout to `CannotAcquireLockException`, the rest to the new `JdbcAccessException`. The `SQLException` is always the
  cause, because the API looks for MySQL error 1062 in the chain to answer 409.
- `JdbcSupportIT`: 16 tests on the real MySQL proving the base class: it joins the use case's transaction, a rollback undoes the
  adapter's writes, also beside JPA in one transaction, tenant filter, audit columns, optimistic lock, duplicate key, soft
  delete, paging, connection release, increasing ids.
- File handling (OOP): `platform/reports/CsvReport` writes a CSV with `java.nio.file` and a try-with-resources writer, and the
  checked, chained `ReportExportException`. `TimetableReportService` and `TicketReportService` write the live timetable and the
  tickets through the existing list use cases, so a report holds only what the caller may see. There is no endpoint: the
  tests (`CsvReportTest`, `ReportExportIT`) are the demo, so the OpenAPI file and the generated frontend client are unchanged.
- `database/`: the lab scripts (`README.md` there). `01_schema` was produced by replaying every Flyway migration into an empty
  MySQL 8.4 and dumping it. All scripts were run in order against it and every statement succeeded. `04_queries_om.sql` holds
  only a header: the queries are the student's own.

## 3. Where the JDBC adapters had to reproduce JPA, and judgement calls

- **No-op writes.** Hibernate sends no `UPDATE`, and so does not bump `version`, when a change leaves a row as it was. The
  adapters compare before they write and do the same (ticket update, occurrence move/cancel/restore/reassign, renames, review
  decisions, change edits).
- **Bulk updates.** `markAllRead` and `supersede` set only what the old bulk JPQL set (`read_at`; `record_status` and
  `updated_at`) and leave the version alone.
- **Tenant.** Reads use the bound tenant, or the tenant nothing is stored under when none is bound, so they find nothing, as
  Hibernate's tenant filter did. A write with no tenant bound is refused. (The plan says `TenantContext.require()` for every
  query; that would have thrown on a read where Hibernate returned nothing.)
- **Time.** `created_at`/`updated_at` use the system clock to the microsecond, as Spring Data auditing did, not the injectable
  `Clock`.
- **Ordering.** Where JPA left the order of rows unspecified, the SQL orders by `id` last so paging is stable. `newId()` is
  strictly increasing within the JVM (RFC 9562 counter), because `id` is also the tie-break for reviews and findings.
- **Public `RowMapper`.** Spring Modulith counts a `protected` nested type as not exposed; it became public when a module's own
  base class (`JdbcReferenceSupport`) named it in a method signature.
- **One small change to platform code outside the adapters:** `PersistenceConfiguration.currentActor()`, so the JDBC adapters
  record the same audit actor as JPA auditing.

## 4. Verified, and not yet

Toolchain: JDK 27 at `C:\Program Files\Java\jdk-27.0.1.0.3` (`JAVA_HOME` still points at JDK 21, and CLAUDE.md still says only
JDK 21 is installed). Built without `-Dmaven.compiler.release`, as CI does.

**Baseline** (untouched branch `dog`, `clean verify`, JDK 27): BUILD SUCCESS, 245 unit tests and 527 integration tests,
772 in all, 0 failures, 0 errors, 0 skipped, 21 minutes.

**After the conversion, run by module as each phase ended** (no existing test edited or skipped; all green):

| Phase | Run | Result |
| --- | --- | --- |
| 1 `JdbcSupport` | `JdbcSupportIT`, `PersistenceConventionsIT`, `ArchitectureTest`, `ModularityTest` | 15 + 12 + 10 + 4 pass |
| 2 notifications | `NotificationApiIT`, `OpenApiContractIT`, architecture | pass |
| 3 claims | timetable and rescheduling suites, `ClaimsTest`, architecture | 156 pass |
| 4 timetable | timetable, rescheduling, notifications, `JdbcSupportIT`, architecture | 189 pass |
| 5 tickets | the same plus `IssueApiIT`, `OpenApiContractIT` | 213 pass |
| 6 reference | reference, academic, access, timetable, rescheduling, issues, notifications, `OpenApiContractIT`, architecture | 273 pass |
| 8 export | `CsvReportTest`, `ReportExportIT`, architecture | 24 pass |

**All unit tests, after the conversion** (`mvnw test`, no Docker): BUILD SUCCESS, 250 tests, 0 failures, 0 errors, 0 skipped.
That is the baseline's 245 plus the 5 of `CsvReportTest`, the expected count. So the Surefire half of the baseline is
reproduced in full; what remains unshown is the Failsafe half (the integration tests, 527 in the baseline) as one run.

Two failures happened on the way and were fixed in the new code, not in a test: `Optional.of(null)` on a `MIN()` row, and
`RowMapper` not public. A third, in `CsvReport` (the header counted as a row), was caught by its own new test.

**Not done: the full `./mvnw verify` after the conversion.** The one attempt was stopped by the system for low memory before any
test had started; it is not a test result. Until it is run, "the same tests as the baseline pass" is **not** shown for the
suites this work did not touch or re-run: `audit`, `identity`, `files`, `notes`, the rest of `platform`, `BackendApplicationIT`,
`ModuleStartupIT` (the module-removal startup matrix) and the other test classes of the baseline that were not re-run. Expected total once
it is run: 772 + 16 (`JdbcSupportIT`) + 5 (`CsvReportTest`) + 5 (`ReportExportIT`) = 798.

To run it (about 5 GB of free RAM, 20 minutes, from `backend/`, with JDK 27 as `JAVA_HOME`):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27.0.1.0.3'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
.\mvnw.cmd -B clean verify *> ..\build-log.txt
```

## 5. Things to know

- `ReferenceScopeDirectoryIT` has a Javadoc `{@link ...JpaScopeDirectory}` that now points at a class that no longer exists. It
  is a comment; the test was not edited, as agreed.
- The lab database has foreign keys (`database/03_foreign_keys.sql`); the application schema still has none, by design.
- Generated files: `backend/openapi.json` and the frontend schema are unchanged, because no API changed.
- The plan was written for branch `om/api`; the work is on `dog`.
- `git rm` was used for the deleted JPA classes, so those deletions are staged. Nothing is committed.
