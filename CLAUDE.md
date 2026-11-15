# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

**Timetia**, a college lecture-rescheduling app, built on **Kibootia**, a modular-monolith template. Kibootia supplies the
platform (sign-in, roles, audit, files, the `notes` example feature); Timetia adds the academic modules. A teacher changes a
lecture of the published weekly timetable by raising a **ticket** (reschedule, cancel, substitute, exchange, extra,
permanent change, revert). The ticket is conflict-checked, optionally reviewed, then **applied** to the **live timetable**.
Representatives can open **issues**, and everyone affected gets in-app **notifications**.

- `backend/`: Spring Boot 4, Java 25 target, Maven wrapper, MySQL + Flyway, Keycloak OIDC resource server.
- `frontend/`: Next.js 16 App Router (React 19, Tailwind 4, shadcn, TanStack Query, react-hook-form + zod). It is also
  the BFF that holds the session.
- `infra/keycloak/`: OpenTofu/Terraform for the local realm and demo users. `compose.yaml` holds the local infrastructure.
- Docs: `kibootia-docs/` (platform ADRs, conventions, guides), `timetia-docs/` (product rules, flows, roles, identity
  contract), and `frontend/docs/`, the most current working spec. Its `backend-guide.md` and `architecture.md` are the
  detailed versions of this file, and `phase-1..3.md` hold the frontend build plan. Where `timetia-docs` and the code
  disagree, the code and `frontend/docs/domain.md` win.

## Commands

From the repository root (pnpm workspace; Node >= 22, pnpm 11):

```powershell
pnpm install
pnpm bootstrap        # once: writes .env from .env.example with generated secrets
pnpm infra:up         # MySQL, Keycloak, Mailpit, Adminer, Grafana LGTM (Docker must be running)
pnpm dev              # backend :8080 + web app :3470 on the host
pnpm reset            # wipe all local volumes, keep .env
pnpm apps:up / apps:down   # run both apps as containers instead (stop `pnpm dev` first)
```

### Backend (run Maven from `backend/`)

CI uses JDK 25. **This machine has only JDK 21**, so always pass `-Dmaven.compiler.release=21`; the code uses no Java 22+
features. In PowerShell, **quote every `-D` argument**. Run Maven with `backend/` as the working directory, because from
anywhere else Spring Modulith writes a stray `build/` folder there. Every `*IT` needs Docker (Testcontainers MySQL). Send
output to a log file and read the file. Do not pipe Maven through `Select-Object -First`.

```powershell
Set-Location backend
# one unit test (Surefire, *Test):
.\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=ClashesTest' '-DfailIfNoTests=false' test *> ..\build-log.txt
# one integration test (Failsafe, *IT):
.\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=none' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=TicketApiIT' '-DfailIfNoTests=false' verify *> ..\build-log.txt
# architecture rules, after any new application class or cross-module call:
.\mvnw.cmd -B '-Dmaven.compiler.release=21' '-Dtest=ArchitectureTest,ModularityTest' '-DfailIfNoTests=false' test *> ..\build-log.txt
# everything (~770 tests, 10-15 min, ~5 GB free RAM; run in the background):
.\mvnw.cmd -B '-Dmaven.compiler.release=21' verify *> ..\build-log.txt
```

If the OS kills a run for low memory, that is not a test failure. Re-run it. `-Dgroups=startup` runs only the startup
smoke tests that CI uses for the module-removal matrix.

### Frontend

```powershell
pnpm --filter kibootia-frontend lint
pnpm --filter kibootia-frontend typecheck
pnpm --filter kibootia-frontend test                                  # Vitest, all
pnpm --filter kibootia-frontend exec vitest run tests/unit/modules.test.ts   # one file
pnpm --filter kibootia-frontend build
pnpm --filter kibootia-frontend test:e2e                              # Playwright; needs the full stack running
```

**Known baseline:** 15 Vitest tests in `tests/unit/{avatar,import-screen,note-attachment}.test.tsx` already fail before
any Timetia change. Any other failure is a regression.

### After any API change (in this order; CI fails if either generated file is stale)

1. Add the new path to the pinned list in `OpenApiContractIT.describesTheApi`
   (`backend/src/test/java/com/kibootia/backend/platform/web/OpenApiContractIT.java`).
2. Run `OpenApiContractIT`. It **rewrites `backend/openapi.json`**.
3. Then run `pnpm --filter kibootia-frontend api:generate` to rewrite `frontend/src/lib/api/schema.gen.ts`. Do not run
   steps 2 and 3 at the same time. Never edit either generated file by hand.

## Backend architecture

Root package `com.kibootia.backend` (never rename it: forks merge from upstream). Each direct sub-package is a Spring
Modulith module:

- Platform: `platform` (shared kernel, depends on nothing), `identity`, `access`, `audit`, `files`, `notes`. `notes` is
  the reference feature to copy; `kibootia-docs/engineering/guides/add-a-feature.md` walks through it.
- Timetia: `academic` (permissions, role templates, `AcademicAccessPolicy`), `reference` (campus and catalogue data),
  `timetable` (weekly versions, dated lecture occurrences, live view, occurrence claims), `rescheduling` (tickets,
  reviews, conflict checks, revert), `issues`, `notifications`.

Inside a module: the **top-level package is the published contract** (ports, records, events). `application/` holds use
cases, `...Store` ports and models. `domain/` holds pure rules with no Spring and no JPA. `persistence/` holds JPA
entities and `Jpa...Store` adapters. `web/` holds package-private controllers, DTOs and hand-written mappers (no
MapStruct).

Cross-module reads go only through published ports:

| What | Port |
|---|---|
| Reference data | `reference.AcademicDirectory` |
| Lectures, conflict checks and applying changes | `timetable.LectureCalendar` |
| Tickets | `rescheduling.TicketVisibility` |
| Who holds a role in a place | `access.ScopeHolders` |
| User names | `platform.directory.UserDirectory` |

Ticket events (`rescheduling.TicketChanged`) feed notifications through durable listeners.

**Rules enforced by tests** (the build fails if one is broken):
- `ModularityTest` pins the module list and **each module's allowed dependencies**. A new module or a new cross-module
  call means extending the pin. `static final` constants are inlined, so they do not count as a dependency.
- ArchUnit rule 7: every public method of an `application` `@Component`/`@Service` needs `@RequiresPermission`,
  `@NoAuthorization("reason")`, or a *direct* `Authorization` call inside that method. A call in a helper or a lambda does
  not count.
- `@Scheduled` methods need `@SchedulerLock`. Listeners use `@DurableEventListener` (never
  `@ApplicationModuleListener`), and their first parameter is a `DomainEvent`.
- Entities never appear in API types. Removable modules (`notes`, `files`), listed in `REMOVABLE_MODULES`, must have no
  dependents.
- Audit action names match `module.resource.action`, with hyphens and never underscores (e.g. `rescheduling.ticket.created`).

**Authorization:** permissions are named `<module>.<resource>.<action>`. Academic permissions are constants in
`academic/AcademicPermissions.java`. All `rescheduling.*` permissions, and all `timetable.live.*` except `view-all`, are
**policy-decided**: they need a facts record (`TicketAccessFacts`, `LectureAccessFacts`, `IssueAccessFacts`), and a
permission-only check answers no. A record the caller may not see is a 404; one they may see but not change is a 403.
Lists filter in the query using `authorization.reach(...)`, so paging stays correct.

**Data:** every table is tenant-scoped (`id BINARY(16)` UUIDv7, `tenant_id`, `version`, audit columns, and unique keys
that include `tenant_id`). Native queries must name `tenant_id` themselves. Migrations live in
`src/main/resources/db/migration/<module>/V<yyyyMMddHHmm>__<module>_<what>.sql`; the timestamp must sort after the
latest existing one. Never edit an existing migration. A new folder must be added to `spring.flyway.locations` in both the
main and the test `application.properties`. Hibernate runs `ddl-auto=validate`, so a `TINYINT` column needs
`@JdbcTypeCode(SqlTypes.TINYINT)`. `hibernate.type.java_time_use_direct_jdbc=true` keeps `LocalTime`/`LocalDate` from
shifting on this IST machine.

**Errors:** throw `ApiException.notFound/conflict/invalid(code, ...)`. The `code` string is what the UI keys its message
on; register new codes in `frontend/src/lib/api/problem.ts` and in `frontend/docs/domain.md`. A blocked ticket apply is
*returned* as an outcome rather than thrown, so the blocked record commits. The controller then maps it to 409
`rescheduling.blocked_by_conflicts`.

**Response DTOs:** put `@Schema(requiredMode = REQUIRED)` on non-null fields and `@Schema(nullable = true)` on nullable
ones. Without the annotation, the generated TypeScript field becomes optional.

## Backend tests

Base classes in `backend/src/test/java/com/kibootia/backend/`:
- `AdminApiTest`: real Spring + Testcontainers MySQL + MockMvc, fake Keycloak, `admin()`/`user(uuid)` callers.
- `TimetableApiTest`: adds a seeded campus (CSE/ME, teachers Rao/Roy/Mehta, rooms, term SEM3 July 2026) and
  `publishStandardTimetable()`.
- `ReschedulingApiTest`: adds the published timetable and a controllable clock (`MutableClockConfiguration`).

Conventions:
- Use **one `@BeforeEach` that builds data per class**, because JUnit does not order several. A superclass's
  `@BeforeEach` runs first.
- When a test adds a table, add its `delete from` to `TimetableApiTest.clean()`, children first.
- Notifications are async after commit, so poll with `Await.until(...)`.
- Each endpoint gets tests for: allowed, denied (403 or 404 as the rule says), unauthenticated or unverified, and, for
  permission-filtered lists, list-vs-record agreement (copy `TicketVisibilityApiIT`).
- `ScopeFixtures.FakeScopeDirectory` is `@Primary`, overriding the real `JpaScopeDirectory`.

## Frontend architecture

- **Request path (do not change):** browser components call `browserApi()`, which goes through the Next.js
  `/api/[...path]` proxy with the cookie and a CSRF header. The proxy calls Spring with the bearer token. Server
  components use `serverApi()` (only for `me`). Never touch the sign-in/BFF code: `src/lib/bff/`, `src/lib/session.ts`,
  `src/lib/oidc.ts`, `src/app/auth/`, `src/app/api/`.
- **Layers:** `src/app/**/page.tsx` only composes a screen inside `<PageShell>`. `src/features/<feature>/` holds `api.ts`
  (TanStack Query hooks; the only file that imports `browserApi`/`unwrap`), screens, `components/`, and pure `lib/`.
  `src/components/ui/` is shadcn: add components with the shadcn CLI and load the `shadcn` skill first; don't hand-write
  them.
- **Registering a feature** takes three places:
  - a `// module:<id>:start` … `:end` block in `src/modules.ts` (nav and protected paths);
  - a literal entry tagged `// module:<id>` in `config.matcher` in `src/proxy.ts`;
  - the page files.
- **Forms:** zod + `applyProblem()` (`src/lib/forms.ts`) maps server field errors onto the form. Copy
  `features/notes/NoteEditor.tsx`.
- **Permissions in the UI** (`usePermissions()`, `<Can>`) are for usability only. The backend enforces them.
- **Component tests:** Vitest with `// @vitest-environment jsdom`, `renderScreen(..., meWith([...]))` and
  `stubApi(...)` from `tests/unit/ui.tsx` and `helpers.ts`. Copy `notes-screen.test.tsx`.

## Working agreements

- Do not commit or push unless asked.
- Backend code style: tabs, records for models, constructor injection with a `this.` prefix. No formatter is configured,
  so match the surrounding code.
