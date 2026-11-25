# Backend guide: adding endpoints to this backend

Read this before any backend task in [phase-1.md](phase-1.md) to [phase-3.md](phase-3.md). Paths are relative to
`backend/src/main/java/com/kibootia/backend/` unless they start with `backend/`.

## 1. Shape

A Spring Boot **modular monolith** checked by Spring Modulith and ArchUnit. Modules: `platform` (shared kernel),
`identity`, `access`, `audit`, `files`, `notes` (platform), and Timetia's `academic`, `reference`, `timetable`,
`rescheduling`, `issues`, `notifications`. Each module has:

```
<module>/                    PUBLISHED surface: interfaces + records other modules may use (ports, events)
<module>/application/        use cases (@Service/@Component), store ports (interfaces), application models (records)
<module>/domain/             pure rules and enums (no Spring, no JPA)
<module>/persistence/        JPA entities, Spring Data repositories, store adapters (Jpa...Store)
<module>/web/                @RestController, request/response records, hand-written mappers
```
Rules enforced by tests (`backend/src/test/java/com/kibootia/backend/ModularityTest.java` and the `arch` tests):
1. A module reads another only through its **published surface** (top-level package). Reference data is read via
   `reference.AcademicDirectory`; lectures via `timetable.LectureCalendar`; tickets via `rescheduling.TicketVisibility`;
   who holds a role in a place via `access.ScopeHolders`; user names via `platform.directory.UserDirectory`.
2. Dependencies between modules must not form a cycle. `ModularityTest` pins the module list and, per module, the modules it
   depends on (`dependenciesOf(...)`). **A new cross-module call needs the pin extended.** `static final` constants are inlined
   by the compiler and do not count as a dependency.
3. **Every public method of a `@Component`/`@Service` in an `application` package** needs `@RequiresPermission(...)` or
   `@NoAuthorization("reason")`, or a **direct** call to `Authorization` (`require`/`can`/`reach`) inside that method (not in a
   helper it calls).
4. Controllers are package-private classes in `web/`, call only use cases, and map by hand (no MapStruct; ADR 0005).
5. Persistence adapters implement a `...Store` port from `application/`. Use cases never see JPA.
6. Audit action names match `[a-z][a-z0-9]*\.[a-z][a-z0-9-]*\.[a-z][a-z0-9-]*` (hyphens, never underscores), e.g.
   `rescheduling.ticket.created`.

## 2. Anatomy of an endpoint (follow an existing one)

Reference slice, smallest first:

| Layer | Example to copy | Notes |
|---|---|---|
| Controller | `notifications/web/NotificationsController.java` (own-data), `reference/web/RoomsController.java` (CRUD) | `@RestController @RequestMapping("/api/v1/...")`; list endpoints take `@ParameterObject @Valid PageQuery query` and return `PageResponse.of(page, mapper)`; sort allow-list via `query.toPageable(Map.of("apiName","entityProp"), defaultSort)` |
| Response record | `notifications/web/NotificationResponse.java` | `@Schema(requiredMode = REQUIRED)` on non-null fields, `@Schema(nullable = true)` on nullable ones. This drives the generated TypeScript types: a missing annotation makes the TS field optional |
| Use case | `notifications/application/NotificationService.java` (`@NoAuthorization`), `issues/application/IssueService.java` (policy checks) | `@Transactional` or `@Transactional(readOnly = true)` on each method |
| Published port | `reference/AcademicDirectory.java` + `reference/application/AcademicDirectoryService.java` | the way another module asks for data |
| Errors | `ApiException.notFound(code, msg)`, `.conflict(code, msg)`, `.conflict(code, msg, errors)`, `.invalid(ProblemCodes.VALIDATION_FAILED, msg, List<ApiFieldError>)`, `new ApiFieldError(field, code, message)` | the `code` string is what the UI keys its message on; add it to `domain.md` section 7 and `frontend/src/lib/api/problem.ts` |
| Authorization | `Authorization authorization` (platform): `require(permission)`, `require(permission, facts)`, `can(...)`, `reach(permission)`, `currentUserId()`, `requireVerified()` | policy-decided permissions (all `timetable.live.*` except `view-all`, all `rescheduling.*`) need a **record** (`facts`) to be satisfied; permission-only checks answer "no" for them |

**Unauthenticated-style lookups for any signed-in person** (the new directory endpoints) follow the
`AcademicDirectoryService` pattern: `@NoAuthorization("...reason...")` plus `this.authorization.requireVerified()` as the
first line, because an unverified account must not read campus data. (`requireVerified()` is a direct `Authorization`
call, so rule 3 is also satisfied.)

### Tenant

All data is tenant-scoped (`TenantContext`); store adapters already filter by the current tenant. New tables copy the
columns `id BINARY(16), tenant_id BINARY(16), version BIGINT, created_at/by, updated_at/by` and `UNIQUE` keys that include
`tenant_id` (see `backend/src/main/resources/db/migration/notifications/V202611152700__notifications_create_notifications.sql`).

### Migrations

`backend/src/main/resources/db/migration/<module>/V<yyyyMMddHHmm>__<module>_<what>.sql`, Flyway, **timestamps must sort after
the latest existing one** (latest today: `V202611152700`). Use `V202611170900`, `V202611171000`, ... for this work. Never edit
an existing migration. Hibernate validates the schema on start (`ddl-auto=validate`), so entity and column types must match
(a `TINYINT` column needs `@JdbcTypeCode(SqlTypes.TINYINT)` on an `int` field).

## 3. After an API change (always, in this order)

1. Add the new path(s) to the pinned list in `backend/src/test/java/com/kibootia/backend/platform/web/OpenApiContractIT.java`
   (`describesTheApi`, the `containsExactlyInAnyOrder(...)` list around lines 86 to 108). Adding an endpoint without this
   fails the test.
2. Run that test: it **rewrites `backend/openapi.json`**:
   ```powershell
   Set-Location backend
   .\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=none' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=OpenApiContractIT' '-DfailIfNoTests=false' verify
   ```
3. From the repository root: `pnpm --filter kibootia-frontend api:generate` (rewrites `frontend/src/lib/api/schema.gen.ts`).
   Do not run steps 2 and 3 at the same time.
4. Commit `openapi.json` and `schema.gen.ts` together with the change (CI fails when `schema.gen.ts` is stale).

## 4. Tests

Test base classes (`backend/src/test/java/com/kibootia/backend/`):

| Class | Gives you |
|---|---|
| `AdminApiTest` | real Spring + MySQL (Testcontainers) + MockMvc; `admin()`, `user(UUID)`, `unverified(UUID)` request post-processors; `get/post/put/delete(who, path, body)`; `read(result, "$.id")`; `signIn(uuid)` (first request creates the profile); `jdbc`; fake Keycloak |
| `TimetableApiTest` | on top: the campus. CSE (division `CSE-A`, batches `CSE-A1`, `CSE-A2`), ME (`ME-A`, `ME-A1`); teachers Rao (`RAO_USER`), Roy (`ROY_USER`), Mehta (`MEHTA_USER`) each with a faculty record; rooms R101, R102, LAB1 (LAB), R201; term SEM3 2026-07-06 to 2026-07-31; helpers `publishStandardTimetable()`, `personWith(role, place)`, `somebody()`, `assign(user, role, place)`, fields `deptCse`, `divisionCseA`, `batchA1`, `facultyRao`, `room101`, `lab1`, `theoryOop`, `labA1` ... |
| `ReschedulingApiTest` | on top: standard timetable published, Rao/Roy/Mehta hold Faculty, a controllable clock (`this.clock.set(Instant)`), helpers `lecture(assignment, "2026-07-06")`, `move(...)`, `cancel(...)`, `raise(userUuid, body)`, `applyOk` |

Conventions: class `XxxApiIT` (Failsafe, `-Dit.test=`), plain unit tests `XxxTest` (Surefire, `-Dtest=`); `@DisplayName`
sentences in plain English; assert with `jsonPath`; for events (notifications) poll with `Await.until(...)` because
listeners run asynchronously after commit. **One `@BeforeEach` per class that builds data** (JUnit does not order several);
a superclass `@BeforeEach` runs first. When a test class adds a table, add its `delete from <table>` to `clean()` in
`TimetableApiTest` **in dependency order** (children first).

Also needed per new endpoint: an unauthenticated/unverified call → 401/403, a person with no right → 403 or 404 (whichever
the rule says), the happy path, and a list-vs-record agreement test if it filters by permission (copy
`TicketVisibilityApiIT`, `LiveTimetableApiIT.listAgreesWithRecords`).

## 5. Running

Machine facts: only JDK 21 installed while the project targets 25, so **always** pass the release override; Docker Desktop must
be running for any IT; PowerShell needs each `-D` quoted. Run Maven from `backend/` (otherwise Spring Modulith writes a stray
`build/` directory elsewhere).

```powershell
Set-Location backend
# one test class (integration):
.\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=none' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=DirectoryApiIT' '-DfailIfNoTests=false' verify *> ..\build-log.txt
# one unit test:
.\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=ClashesTest' '-DfailIfNoTests=false' test *> ..\build-log.txt
# everything (about 770 tests, 10 to 15 minutes; run in the background; needs about 5 GB free memory):
.\mvnw.cmd -B '-Dmaven.compiler.release=21' verify *> ..\build-log.txt
```
Redirect to a log file and read it; do not pipe Maven through `Select-Object -First`. A run killed by the OS for low memory is
not a test failure; close heavy programs and re-run.

## 6. Pieces to know before touching them

- **Ticket authorization** is `academic/domain/AcademicAccessPolicy.java` reading `TicketAccessFacts`
  (`rescheduling/application/TicketFacts.java`). New ticket endpoints call `support.facts(ticket)` and
  `authorization.require/can(AcademicPermissions.X, facts)`; a ticket the caller may not see is answered 404 via
  `support.requireSeen(facts)` first.
- **Live lecture reads** go through `timetable.LectureCalendar.lecture(id)` (returns `LectureView`, which implements
  `LectureAccessFacts`).
- **Names** for responses come from `AcademicDirectory` (`assignments`, `faculties`, `rooms`, `division`, ...) and
  `UserDirectory.find(ids)` → `Map<UUID, UserSummary(displayName)>`; batch them (one lookup per kind per response page).
- **Permission strings** are constants in `academic/AcademicPermissions.java`.
- **Events**: ticket events are `rescheduling.TicketChanged` (published by `TicketNotices`); notifications consume them in
  `notifications/application/NotificationEvents.java` via `@DurableEventListener`.
- Dependencies of `ModularityTest` today: `reference` ← platform only; `timetable` → reference, academic, platform;
  `rescheduling` → timetable, reference, academic, platform; `issues` → reference, rescheduling, academic, platform;
  `notifications` → access, issues, rescheduling, platform. New edges in this plan are listed in each phase file.

## 7. Style

Tabs for indentation, Javadoc sentences in plain English, records for models, constructor injection with `this.` prefix,
one blank line between members. No formatter plugin is configured; copy the surrounding code. The architecture tests are
`ArchitectureTest.java` and `ModularityTest.java` in the test root; run them (`-Dtest=ArchitectureTest,ModularityTest`)
after any new class in `application` or any new cross-module call.
