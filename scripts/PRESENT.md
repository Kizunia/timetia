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

## 3. Seed the fake data (needed after every reset)

The data is made through the real API from the signed-in web app, so it needs a login.

1. Open <http://localhost:3470>, sign in as **`demo`** (password `DEMO_USER_PASSWORD` in `.env`), sign out. This only creates the account.
2. Sign in as **`demo-admin`** (password `DEMO_ADMIN_PASSWORD`).
3. Press **F12 → Console**, paste the whole of **`scripts/seed-college.browser.js`**, press Enter. It takes about a minute and ends with
   `College ready`. (If it says the college already exists, nothing was changed.)

What you get (1 college, 2 branches):

| Branch | Divisions | Batches | Students |
| --- | --- | --- | --- |
| CSE | CSE-A, CSE-B, CSE-C | 2 each (CSE-A1, CSE-A2, ...) | 5 per batch |
| ME | ME-A, ME-B | 2 each | 5 per batch |

That is 5 divisions, 10 batches, 50 student accounts (invited, so their emails are in Mailpit), 6 teachers, 8 rooms, a 4-week term
starting next Monday and a published weekly timetable for every division. `demo` is Dr Demo (Faculty), `demo-admin` is Dr Admin
(Faculty + HOD of CSE). The first student of each batch is also its BR, the first of each division also its CR.
To change the size (divisions, batches per division, students), edit the `branches` list and the `[1, 2]` / `s <= 5` loops in the script.

The older, smaller `scripts/demo-seed.browser.js` (one division) still works on its own but must not be run after the big one.

## 4. Demo flow for the presentation (about 10 minutes)

Use two windows: one normal for `demo-admin`, one private window for `demo`. In the private window sign in as `demo`.

1. **Timetable** (`/timetable`): Next until the term starts; filter by division and batch; "Only my lectures" as `demo`; click a lecture.
2. **Reschedule** (as `demo`): click the Monday OOP lecture → Change this lecture → Reschedule → Thursday 09:00. Only free rooms are offered.
   Create the ticket → Apply → the timetable shows "Moved".
3. **Conflict**: reschedule another lecture into an occupied slot → "Something is in the way", Apply is disabled → edit the time → apply.
4. **Review**: raise a ticket, Send for review, pick the HOD. As `demo-admin` open `/tickets` → **To review** → Review → Approve → it is applied.
5. **Reject and escalate**: reject with a reason; as `demo` Edit (round 2) or Take to the HOD.
6. **Other kinds**: Cancel, Substitute, Exchange, Permanent change (needs the HOD), New extra lecture (`/tickets/new`), and Revert.
7. **Database**: open Adminer and show `lecture_occurrence`, `occurrence_claim` (the double-booking guard), `timetable_change_ticket`, `ticket_review`.
   The full explanation (schema, every important query, viva questions) is in `timetia-docs/engineering/database.md`.

## 5. What is built, and what is not

Built: the live timetable, tickets of every kind with review, conflict checking, free-room suggestions, revert, names everywhere.
Not built in the web app yet (the backend for them exists, see `scripts/DEMO.md` section 5 for the console calls): the issues
screens, the notifications bell and page, and the admin screens for campus data and timetables. Browser exit checks of the
timetable and ticket screens have not been run by a machine; walk through section 4 once before presenting.
