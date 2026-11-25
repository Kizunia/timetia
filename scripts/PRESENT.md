# Getting Timetia ready to present

All commands are PowerShell from the repository root. Docker Desktop must be running. About 5 GB of free RAM is needed;
close heavy programs first.

## 1. Start everything (normal case)

```powershell
pnpm infra:up      # MySQL, Keycloak (+ realm setup), Mailpit, Adminer. Wait until it returns.
pnpm dev           # backend :8080 (API) and web app :3470. Wait for "Started BackendApplication". Ctrl+C stops both.
```

| What | Where |
| --- | --- |
| **The web app (use this)** | <http://localhost:3470> |
| API documentation | <http://localhost:8090/actuator/swagger-ui> |
| Database browser (Adminer) | <http://localhost:8081> (server `mysql`, user `kibootia_app`, password `MYSQL_PASSWORD` in `.env`, database `kibootia`) |
| Emails (invitations) | <http://localhost:8025> |

Port 8080 is only the API; opening it in a browser shows nothing useful.

## 2. Start from zero (reset everything)

```powershell
pnpm reset         # DELETES all local data (database, Keycloak users), keeps .env
pnpm infra:up
docker ps -a --filter name=kibootia-keycloak-config   # must say "Exited (0)"
pnpm dev
```

Problems seen on this machine:
- `container name "/kibootia-xxx" is already in use`: a stopped old container holds the name. `docker rm kibootia-xxx` (stopped ones only), then `pnpm infra:up` again.
- `kibootia-keycloak-config` says `Exited (1)` with "registry.opentofu.org ... Timeout": a slow network. Run `docker start -a kibootia-keycloak-config` until it ends with "Apply complete".
- Backend says "Access denied for user 'kibootia_app'": the database volume was made with another `.env`. Run `pnpm reset`.
- Run the frontend unit tests (`pnpm --filter kibootia-frontend test`) from **PowerShell**, not Git Bash.

## 3. Seed the fake data: one command, no manual work

With `pnpm infra:up` and `pnpm dev` running (the backend must say "Started BackendApplication"):

```powershell
pnpm seed          # about 3 minutes; safe to run again (it skips what exists)
```

It creates the sign-in accounts in Keycloak, signs each in once in a headless browser, builds the college through the real API and
adds sample activity. The data starts in the **current week** (the term begins on this week's Monday, 4 weeks long).

**Accounts** (all use the password `DEMO_USER_PASSWORD` from `.env`, except `demo-admin`, which uses `DEMO_ADMIN_PASSWORD`):

| Username | Who | What to look at |
| --- | --- | --- |
| `demo-admin` | Dr Admin, CSE teacher, **HOD of CSE**, Super Admin | reviews tickets, Administration area, all CSE tickets |
| `demo` | Dr Demo, CSE teacher (OOP in CSE-A/B/C) | raise tickets, "Only my lectures" |
| `rao` | Dr Rao, CSE teacher | a second teacher's own timetable |
| `mehta`, `iyer`, `khan` | ME teachers | the ME timetable |
| `hod.me` | HOD of ME | sees all ME tickets |
| `cr.cse-a` | class representative of CSE-A | opens and sees issues of CSE-A |
| `stu.cse-a1` | student and batch representative of CSE-A1 | sees only CSE-A1's lectures |
| `stu.me-a2` | student of ME-A2 | sees only ME-A2's lectures |

The 50 other students (5 per batch) are invited accounts (emails in Mailpit); they have no password yet. The college has 2 branches
(CSE with divisions A, B, C; ME with A, B), 2 batches in each division, 6 teachers, 8 rooms. Sample activity: one applied
reschedule, one reschedule waiting for the HOD (`demo-admin`), one cancel draft, one issue from the CR mentioning Dr Demo.
To change the size edit the `branches` list and loops in `scripts/seed-college.browser.js`.

If `pnpm seed` fails: make sure the stack is up and the backend finished starting; fix, then run it again.

## 4. Demo flow for the presentation (about 10 minutes)

Use two windows: a normal one for `demo-admin` and a private window for `demo`.

1. **Timetable** (`/timetable`): this week is shown; filter by division and batch; as `rao` or `demo` tick "Only my lectures";
   sign in as `stu.cse-a1` to show a student sees only their batch.
2. **Reschedule** (as `demo`): click a lecture that is still ahead (not one that has started) -> Change this lecture -> Reschedule.
   Only free rooms are offered. Create -> Apply -> the timetable shows "Moved".
3. **Conflict**: move a lecture into an occupied slot -> "Something is in the way", Apply is disabled -> edit the time -> apply.
4. **Review**: as `demo-admin` open **Tickets -> To review** (the seeded ticket is waiting) -> Review -> Approve -> applied.
   The bell in the header counts new notifications; open **Notifications**.
5. **Reject and escalate**: reject with a reason; as `demo` Edit (round 2) or Take to the HOD.
6. **Other kinds**: Cancel, Substitute, Exchange, Permanent change (needs the HOD), New extra lecture (Tickets -> New extra lecture), Revert.
7. **Issues**: as `cr.cse-a` open **Issues** -> Open an issue (mention a teacher). As `demo` the mention shows in Notifications;
   open the issue and close it.
8. **Database**: open Adminer and show `lecture_occurrence`, `occurrence_claim` (the double-booking guard), `timetable_change_ticket`,
   `ticket_review`. The full explanation (schema, queries, viva questions) is `timetia-docs/engineering/database.md`.

## 5. What is built

Live timetable, tickets of every kind with review/escalation/revert, conflict checks, free-room suggestions, notifications
(bell and page), issues (open, mention, link a ticket, close), campus and timetable setup under Administration,
role-based visibility, audit log, user and role administration. The longer click-through is [DEMO.md](DEMO.md).
Deferred, and not built: see `timetia-docs/modules/lecture-rescheduling/open-questions.md`.
