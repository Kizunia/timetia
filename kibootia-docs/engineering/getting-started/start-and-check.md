# Start Kibootia and See What Works

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** the container route below (`pnpm apps:up`) was run on a Windows machine on 2026-11-10 from freshly reset volumes, and the checks in section 4 passed there and in CI (run 42780048416, pull request #5). The host route (`pnpm dev`) was last run for milestone 1 and is not re-run for milestone 3. Not run on macOS or Linux. The photo checks (steps 16 to 18) describe milestone 4. Their automated counterpart, the avatar end-to-end story, passed against the full stack in CI (run 42873216224, pull request #6); nobody has walked through the steps by hand yet.

## Goal

Configure and start every part of Kibootia, know what it does today and what it does not do yet, and see each main feature working in a browser, in the mail catcher, in the database and in the automated tests.

## What exists today

Milestones 0 to 4 are built ([roadmap](../../product/roadmap/README.md), [implementation status](../../product/roadmap/implementation-status.md)).

| Area | What you can do or see | Built in |
|---|---|---|
| Sign-in | Sign in through the Next.js server with Keycloak (Authorization Code with PKCE). The browser never holds a token, only an encrypted session cookie; sessions refresh by themselves; sign-out ends the Keycloak session too | 1 |
| Protected API | Spring checks every token (signature, issuer, expiry, audience), refuses machine tokens, and answers every error as RFC 9457 Problem Details with a code and a trace ID | 1, 2 |
| Observability | One trace across the web app and the API in Grafana, structured JSON logs with the trace ID, a health endpoint | 1 |
| Platform conventions | UUIDv7 keys, tenant column, soft delete with retention purge, optimistic locking, paging and sorting, a generated OpenAPI description and typed client, durable events with retry, lock-protected jobs, an append-only audit log | 2 |
| A reference feature | A notes API (create, read, update, delete, restore) that shows every convention. Backend only: there is no screen for it yet | 2 |
| Profiles | A profile is created the first time someone uses the API; name and email follow Keycloak. The profile page shows them, the roles, and a colour scheme preference | 3 |
| Roles and permissions | Permissions are defined in code; administrators create and edit roles, give them to users, and a change applies on the user's next request. The administrator role is protected and the last administrator who can sign in cannot be removed | 3 |
| User administration | Invite by email, disable, enable, delete, restore, and erase for good. Keycloak sends the mail; changes reach Keycloak even after an outage | 3 |
| Self-registration and verification | Anyone can register on Keycloak's page. Until they verify their email address they can only see their own profile, ask for the email again and sign out | 3 |
| Audit log screen | Every administrative change, newest first, with who did it and to whom; an erased user shows as "deleted user" | 3 |
| Rate limiting | Invitations, verification mail and administrative actions are limited per user, answering 429 with `Retry-After` | 3 |
| Avatars and object storage | Upload, replace and remove a photo on your profile (JPEG, PNG or WebP, up to 5 MB). The server stores a square 64 and 256 pixel copy and drops everything else in the file, such as the camera's location. Others see your photo only with the permission to see profiles. The files live in RustFS, an S3-compatible server; the module can be switched off | 4 |
| Quality gates | Architecture rules, 341 backend tests, 168 frontend tests, 22 end-to-end tests with accessibility checks, container image scan and secret scan, all in CI (milestone 4: run 42873216224) | 0 to 4 |

**Not built yet:**

- Files other than avatars: the storage module is generic, but only the avatar uses it so far. An administrator cannot remove someone else's photo.
- A screen for the notes example, and the check that the application still builds with a module removed ([milestone 5](../../product/roadmap/milestone-5-example-feature.md)).
- The production deployment, the production start-up guards and the first release ([milestone 6](../../product/roadmap/milestone-6-production-readiness.md)).
- A screen for failed events (the API exists: `GET /api/v1/failed-events`).
- Changing your name, email or password inside Kibootia: those stay in Keycloak's account console, which the profile page links to.

## 1. Prerequisites

| Needed | For |
|---|---|
| Docker Desktop (running) | Everything: the databases, Keycloak, the mail catcher, and the applications in containers |
| Node.js 22 or newer and pnpm 11 | The setup scripts and the web app (`corepack enable` gives you the pinned pnpm) |
| JDK 25 | Only for the host route (`pnpm dev`) and for running the backend tests |

## 2. Configure

1. `pnpm install` from the repository root.
2. `pnpm bootstrap` creates `.env` from `.env.example` with freshly generated secrets. It never overwrites a value you have; run it again after an update to pick up new variables. `.env` is never committed.

Everything is configured through that one file. You normally change nothing in it. The ones you may care about:

| Variable | What it does | Default |
|---|---|---|
| `DEMO_USER_PASSWORD` | Password of the ordinary demo user `demo` | generated |
| `DEMO_ADMIN_PASSWORD` | Password of the demo administrator `demo-admin` | generated |
| `KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL` | The address that becomes the first administrator when it signs in with a verified email address | `demo-admin@kibootia.localhost` |
| `KEYCLOAK_ADMIN_PASSWORD` | Password of Keycloak's own console (`admin`) | generated |
| `COMPOSE_PROFILES` | `observability` also starts Grafana; remove it to save memory. `files` starts RustFS, the object store; without it, photos cannot be saved (uploads answer 503) unless you also set `KIBOOTIA_FILES_ENABLED=false` | `observability,files` |
| `KIBOOTIA_FILES_ACCESS_KEY`, `KIBOOTIA_FILES_SECRET_KEY` | The login of the local object store | generated |

**If you made your `.env` before milestone 4:** run `pnpm bootstrap` to add the two storage keys (it keeps every value you have), then add `,files` to `COMPOSE_PROFILES` in `.env` by hand: bootstrap never changes a value. Then `pnpm infra:up` starts RustFS.

Every setting, with its default and whether it is secret, is in [application configuration](../../operations/configuration/application-configuration.md). Rate limits, the retention periods and the Keycloak address have settings of their own there.

## 3. Start

Run from the repository root, with Docker running.

**With the applications in containers (the route that was checked):**

```text
pnpm infra:up     # databases, Keycloak (the realm is applied for you), Mailpit, Adminer, Grafana
pnpm apps:up      # builds the backend and web images and starts both; waits until they are healthy
```

The first start downloads several images and builds two more, which takes a few minutes. If you ran `pnpm infra:up` before, `pnpm apps:up` alone is enough.

**Or with the applications on your machine** (for working on the code): `pnpm infra:up`, then `pnpm dev`. Stop it with Ctrl+C. Do not run both routes at once; they use the same ports.

To stop: `pnpm apps:down` stops only the two applications; `pnpm infra:down` stops the rest. To throw everything away and start from nothing, `pnpm reset` deletes the local databases and the realm state and starts the infrastructure again; run `pnpm apps:up` after it. Your `.env` is kept.

### Where everything is

| What | Address | Sign in with |
|---|---|---|
| The web app | <http://localhost:3470> | `demo` or `demo-admin`, see below |
| Keycloak console | <http://localhost:8180> | `admin` and `KEYCLOAK_ADMIN_PASSWORD` |
| Mailpit (every email Keycloak sends) | <http://localhost:8025> | nothing |
| Grafana (traces and logs) | <http://localhost:3001> | `admin` / `admin` |
| Adminer (the MySQL database) | <http://localhost:8081> | system MySQL, server `mysql`, user `kibootia_app`, password `MYSQL_PASSWORD`, database `kibootia` |
| The API | <http://localhost:8080> | needs a token; use the web app |
| API health, OpenAPI, Swagger UI | <http://localhost:8090/actuator/health> | host route only; the containers keep this port inside |

The two demo users are in `.env`: **`demo`** is an ordinary user, **`demo-admin`** becomes the administrator the first time it signs in.

## 4. See it working

Do these in order; each builds on the one before. Use a private window for the second person.

| # | Check | How | What you should see |
|---|---|---|---|
| 1 | The stack is healthy | `docker ps`, and open <http://localhost:3470/healthz> | Every container `healthy`; the health page answers 200 |
| 2 | Sign-in | Open <http://localhost:3470>, **Sign in** as `demo` | Keycloak's login page, then the home page saying you are signed in. No tokens in the page source: the session is an HttpOnly cookie |
| 3 | An ordinary user sees no administration | As `demo`, open <http://localhost:3470/admin> | "You do not have access to administration", and no **Administration** link in the header. **Your profile** works and shows the role *User* |
| 4 | The first administrator | Sign out. Sign in as `demo-admin` | An **Administration** link appears; the profile shows the roles *User* and *Administrator* |
| 5 | Roles | **Administration, Roles, New role**. Name it *Reviewers*, tick *View roles*, create | The role opens with its permissions. Open *Administrator*: every permission is ticked and nothing can be changed |
| 6 | Invitation by email | **Users, Invite user**; give an address and tick *Reviewers* | The user appears as *Invited*. Open Mailpit: Keycloak sent the invitation |
| 7 | Accepting it | In a private window open the link from the mail, set a password and a name, then sign in with the address | The new user's profile shows *User* and *Reviewers*, and they can see **Roles** but not **Users** |
| 8 | Disabling is immediate | As the administrator open that user, **Disable**; in the private window reload | The private window shows "Your account is disabled". **Enable** brings them back on the next reload |
| 9 | Roles apply on the next request | Uncheck *Reviewers* for them and **Save roles**; reload in the private window | Their **Roles** section is gone |
| 10 | The last administrator | Open your own user page | No **Disable** or **Delete**; unticking *Administrator* and saving is refused with "last administrator who can sign in" |
| 11 | Self-registration and verification | Sign out; **Sign in**, then **Register** on Keycloak's page | You land in the app with a notice "Verify your email address"; **Administration** is not available. Mailpit holds the verification mail: follow its link, choose **Click here to proceed**, return to the app and choose **I have verified**. The notice goes away |
| 12 | Delete and erase | As the administrator, delete a user, **Restore** them, delete again, **Erase for good** | The user leaves the list of deleted users shortly after. Their notes (if any) and Keycloak account are gone |
| 13 | The audit log | **Administration, Audit log** | Entries for the role, the invitation, the role assignments, the disable and enable, the deletion and the erasure, newest first. The erased user shows as "Deleted user" and no email address appears anywhere |
| 14 | Rate limiting | Invite 21 addresses within an hour as the same administrator (this creates 20 invited users) | The 21st is refused (429) and the screen says to wait |
| 15 | The data | In Adminer open the tables `user_profile`, `role`, `role_assignment`, `audit_entry` | Profiles keyed by the Keycloak user ID; roles and assignments; audit rows holding only user IDs, no names or emails |
| 16 | A photo | As `demo`, open **Your profile**, choose **Upload a photo** and pick a JPEG, PNG or WebP | "Photo saved", and the photo appears on the page and in the header. Choose a text file or one over 5 MB: the page refuses it without sending it |
| 17 | The photo is private and resized | As `demo-admin`, open the user `demo` under **Administration, Users**. Then open <http://localhost:9001> and sign in with the two storage keys from `.env` | The administrator sees the photo; a user without the permission to see profiles is refused (403) when asking for someone else's. In the console the bucket `kibootia` holds two small PNGs for the photo (64 and 256 pixels), not your original, and no public link exists |
| 18 | Replace and remove | **Replace photo**, then **Remove photo** | The picture changes, then the initials come back. The files of removed photos stay (hidden) for 30 days, and are removed with the user when the user is erased |
| 19 | One trace across both applications | In Grafana, Explore, the Tempo data source, search service `kibootia-backend` | A trace that starts in the web app and continues into the API |

**The same checks, automated.** These cover steps 2 to 13 against the running stack, each page with an accessibility check:

| Command | What it runs | Needs |
|---|---|---|
| `pnpm --filter kibootia-frontend exec playwright install chromium` | Installs the test browser, once | Nothing running |
| `pnpm --filter kibootia-frontend test:e2e` | 22 browser tests: sign-in, administration from invitation to erasure, registration with verification, denied access, and the avatar story (the last 8 are the avatar story) | The stack running (step 3), with the `files` profile |
| `cd backend && ./mvnw verify` | 103 fast and 238 integration tests, including the Keycloak adapter against a real Keycloak and Mailpit, and the storage contract against a real RustFS | Docker, JDK 25 |
| `pnpm --filter kibootia-frontend lint`, `typecheck`, `test` | Lint, type check and 168 unit tests | Nothing running |

The end-to-end tests create their own users, roles and audit entries, so after running them the lists in the app hold test data. `pnpm reset` clears it.

## Troubleshooting

- **Saving a photo says a service is not available.** The object store is not running or not reachable. Check that `COMPOSE_PROFILES` in `.env` includes `files` and run `pnpm infra:up`; `docker ps` should list `kibootia-rustfs` as healthy. Everything except photos works without it.
- **Starting the backend fails naming `kibootia.files.access-key`.** The two storage keys are missing from `.env`. Run `pnpm bootstrap`, or set `KIBOOTIA_FILES_ENABLED=false` to run without files.

- **Keycloak or the backend stays unhealthy right after you created a new `.env`.** The old database volumes were made with the old passwords. Run `pnpm reset`.
- **A port is in use.** Find what holds it before stopping anything; it may belong to another project. The services fail rather than move to another port, because the sign-in redirect addresses are fixed.
- **`docker compose` says a build metadata file is locked** (Windows). Run `pnpm apps:up` again; the images are already built.
- **No invitation or verification mail arrives.** Open Mailpit; if it is empty, look at the backend log for a Keycloak error. A realm built outside OpenTofu must give the `kibootia-admin` client the `kc-admin-api` default scope, otherwise every call to Keycloak is refused ([authentication](../../architecture/identity/authentication.md)).
- **Following the link in a mail does nothing.** Keycloak first shows a page with **Click here to proceed**; the action runs after that click.
- **The notice about verification stays after you verified.** Choose **I have verified**: the access token still says otherwise until the session is refreshed, which that button does.
- **You are sent back to sign in after a long time.** A session lasts ten hours at most, however often it refreshes.

## Related documents

[Local setup](local-setup.md), [application configuration](../../operations/configuration/application-configuration.md), [user lifecycle](../../architecture/identity/user-lifecycle.md), [authorization](../../architecture/identity/authorization.md), [implementation status](../../product/roadmap/implementation-status.md), [testing strategy](../testing/strategy.md).
