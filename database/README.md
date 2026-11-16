# Timetia lab database

SQL scripts for the **SE2004 Database Management Systems** lab. They build a separate MySQL database, `timetia_lab`, from the
tables of the Timetia application. The application's own database is owned by Flyway and is never touched by these scripts.

| Script | What it is | Syllabus |
| --- | --- | --- |
| `01_schema.sql` | The 18 domain tables: DDL, primary and unique keys, `CHECK` constraints, generated columns. Produced by replaying the Flyway migrations and dumping the result. | Practical 3, 4 |
| `02_seed.sql` | Sample data: a small campus, three published timetables with four weeks of lectures and their claims, and five tickets in different states. | Practical 4 (DML) |
| `03_foreign_keys.sql` | 49 foreign keys, with `ON DELETE CASCADE` where a child has no life of its own, and a worked demonstration. | Practical 3, 4 (referential integrity) |
| `04_queries_<name>.sql` | **Each member's own 20 or more queries.** Written by that member; the header only lists the tables and the topics to cover. | Practical 5, 6 |
| `05_views_indexes.sql` | Three views, three indexes, and an `EXPLAIN` before and after on a table of 50,000 rows. | Practical 7 |
| `06_dcl.sql` | Roles, users, `GRANT`, `REVOKE`, column-level grants, and how to try each. | Practical 7 |
| `07_tcl.sql` | `COMMIT`, `ROLLBACK`, `SAVEPOINT`, a locking read and isolation levels, with the matching JDBC calls. | Practical 8 |

## Running them

Any MySQL 8.4 will do. With Docker:

```powershell
docker run -d --name timetia-lab -e MYSQL_ROOT_PASSWORD=lab -p 3308:3306 mysql:8.4
# wait about 20 seconds, then, from this folder:
Get-Content 01_schema.sql, 02_seed.sql, 03_foreign_keys.sql | docker exec -i timetia-lab mysql -uroot -plab
Get-Content 05_views_indexes.sql | docker exec -i timetia-lab mysql -uroot -plab -t
```

Run `01`, `02`, `03` in that order, then `05` before `06` (it grants on the views `05` creates). `04` and `07` can be run at
any point after `03`. `07` changes the data; to start again, `DROP DATABASE timetia_lab;` and re-run `01` to `03`.

The scripts were run in this order against an empty MySQL 8.4 when they were written, and every statement succeeded. The
statements shown in comments as failing (`ERROR 1451`, `1452`, `1143`, `1142`) were run and fail as written.

## Why the lab database differs from the application's

- **Foreign keys.** The application schema has none, on purpose: its modules refer to each other's rows by id only, so a module
  can change or be removed without the database tying it to the others. The lab asks for referential integrity, so `03` adds
  the keys to the lab database only.
- **Fewer tables.** Sign-in and roles, the audit log, files, notes, issues and the platform's event tables are left out; they
  are plumbing the course does not mark.
- **One tenant.** Every table has a `tenant_id` (the application is multi-tenant), but the lab has a single tenant, so the
  views and your queries need not filter on it. The application's SQL always does.
- **Indexes and views** in `05` exist only here. An index that proved useful in the application would be added by a new Flyway
  migration.

## Where the application's SQL is

The hand-written SQL of the application, which the lab queries resemble, is in the `Jdbc*Store` classes under
`backend/src/main/java/com/kibootia/backend/*/persistence/` (notifications, timetable, rescheduling, reference). See
`timetia-docs/engineering/jdbc-conversion-report.md`.
