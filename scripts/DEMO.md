# Running Timetia and trying what has been built

The web app has no Timetia screens yet (only users, roles, audit, import, profile, notes). The lecture-rescheduling work is
an API, so you try it from the browser console of the signed-in app, with a seed script that builds a small campus. This
file is the walk-through. All commands are PowerShell, from the repository root.

## 1. What you need

- **Docker Desktop**, running.
- **Node 22+** and **pnpm 11** (`corepack enable`).
- **A JDK.** The project asks for Java 25. With only Java 21, tell Maven to compile for 21 (step 4).
- **About 5 GB of free memory.** The stack is MySQL, Keycloak (and its Postgres), Mailpit, the backend and Next.js. Close
  heavy programs first; Docker or Maven will be killed if the machine runs out.

## 2. Start it

```powershell
pnpm install
pnpm bootstrap     # creates .env with random secrets; the demo passwords are DEMO_USER_PASSWORD and DEMO_ADMIN_PASSWORD in it
pnpm infra:up      # MySQL, Keycloak (+ realm), Mailpit, Adminer. Give it a minute or two.
docker compose ps  # wait until keycloak is healthy and keycloak-config has exited with code 0
```

## 3. Start the apps

```powershell
$env:MAVEN_ARGS = '-Dmaven.compiler.release=21'   # only if you have no JDK 25
pnpm dev                                          # backend :8080 and web app :3470; Ctrl+C stops both
```

Wait for the backend to log `Started BackendApplication`. The first start migrates the database and seeds the roles
(Super HOD, HOD, Faculty, CR, BR, Student).

| Where | What |
| --- | --- |
| <http://localhost:3470> | the web app (sign in with Keycloak) |
| <http://localhost:8090/actuator/swagger-ui> | every endpoint, documented (read it; "Try it out" needs a token, so call the API from the console instead) |
| <http://localhost:8081> | Adminer: the database (server `mysql`, user `kibootia_app`, password `MYSQL_PASSWORD` from `.env`, database `kibootia`) |
| <http://localhost:8025> | Mailpit: the emails the app sends |

## 4. Make the demo campus

1. In a window, open <http://localhost:3470>, sign in as **`demo`** (password: `DEMO_USER_PASSWORD` in `.env`), then sign out.
   This only creates the account.
2. Sign in as **`demo-admin`** (`DEMO_ADMIN_PASSWORD`). Open the developer tools (F12), **Console**.
3. Paste the whole of `scripts/demo-seed.browser.js` and press Enter. It prints `Demo campus ready`.

You now have: department CSE, division CSE-A with batches A1 and A2, a four-week term starting next Monday, and the weekly
timetable published. **Dr Demo** (account `demo`, role Faculty) teaches OOP theory and the A1 lab. **Dr Admin** (account
`demo-admin`, roles Faculty and HOD of CSE) teaches DBMS theory and the A2 lab. A Super Admin on its own has no ticket
powers, which is why `demo-admin` also holds those two roles.

You need **two people at once** for reviews. Use a second browser profile or a private window for `demo`, so the two
sessions do not replace each other. In that second window paste this small helper (the seed script defines the same `tt`
in the first):

```js
window.tt = { call: async (m, p, b) => { const r = await fetch(p, { method: m, headers: { "content-type": "application/json", "x-timetia-request": "1" }, body: b === undefined ? undefined : JSON.stringify(b) }); const t = await r.text(); const j = t ? JSON.parse(t) : null; if (!r.ok) throw new Error(r.status + " " + t); return j; } };
tt.get = (p) => tt.call("GET", p); tt.post = (p, b) => tt.call("POST", p, b ?? {}); tt.put = (p, b) => tt.call("PUT", p, b);
```

A refused request throws with the status and the problem body, which is how you see the rules at work (404 = you may not
see it, 403 = you see it but may not do it, 409 = the state or a conflict forbids it, 400 = a bad field).

## 5. Things to try

Setup in either window (reads the first week from the live timetable):

```js
const iso = (d) => d.toISOString().slice(0, 10);
const week = (await tt.get(`/api/v1/timetable/live?from=${iso(new Date())}&to=${iso(new Date(Date.now() + 60 * 864e5))}&size=100`)).items;   // at most 62 days at a time
const oop = week.find(l => l.subjectCode === "OOP" && l.sessionType === "THEORY");     // Monday 9-10, R101
const dbms = week.find(l => l.subjectCode === "DBMS" && l.sessionType === "THEORY" && l.mode === "IN_PERSON");
const day = (d, n) => { const x = new Date(d + "T12:00:00Z"); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10); };
const slot = (date, from, to, roomId) => ({ date, startTime: from, endTime: to, mode: "IN_PERSON", roomId });
const thursday = day(oop.date, 3);                                                      // free
```

**The live timetable** (as `demo`, then `demo-admin`): `week.length` is 20. `changed`, `cancelled`, `extra` and
`originalDate` mark what tickets did.

**Reschedule, applied by its author** (as `demo`, who teaches OOP):

```js
let t = await tt.post("/api/v1/tickets", { type: "RESCHEDULE", lectureId: oop.id, description: "Move to Thursday", target: slot(thursday, "09:00", "10:00", oop.roomId) });
t.status; t.conflicted;                       // DRAFT, false
t = await tt.post(`/api/v1/tickets/${t.id}/apply`);   t.status;   // APPLIED, approvalPath SELF
(await tt.get(`/api/v1/timetable/live/${oop.id}`)).changed;      // true
```

**A conflict**: ask for the DBMS slot (Monday 10-11, R101). The ticket comes back `conflicted: true` with `conflicts`
(`ROOM_OCCUPIED`, `DIVISION_BUSY`...); `/apply` answers 409 and nothing moves.

**A review** (needs both windows). As `demo`, raise another reschedule, then ask `demo-admin` to review it (the id of each
account is in the seed script's output, `demoCampus.adminUserId`):

```js
await tt.post(`/api/v1/tickets/${t2.id}/submit`, { reviewers: [{ userId: "<demo-admin id>", capacity: "HOD" }] });
```

As `demo-admin`: `(await tt.get("/api/v1/tickets")).items`, then
`await tt.post(`/api/v1/tickets/${id}/review`, { decision: "APPROVE" })`. The last approval applies the ticket
(`approvalPath: "REVIEWED"`). `REJECT` needs a `reason`; the author can then edit and resubmit (`PUT /api/v1/tickets/{id}`
with the current `version`) or `POST .../escalate` to an HOD.

**The other kinds** (same `POST /api/v1/tickets`, then `/apply`):

| Kind | Body |
| --- | --- |
| Cancel | `{ type: "CANCEL", lectureId, reason: "Away" }` |
| Substitute | `{ type: "SUBSTITUTE", lectureId, substituteFacultyId: demoCampus.drAdmin }` (the other teacher takes the lecture) |
| Extra lecture | `{ type: "EXTRA", assignmentId: demoCampus.oopTheory, target: slot(thursday, "09:00", "10:00", oop.roomId) }` |
| Exchange | `{ type: "EXCHANGE", lectureId: oop.id, otherLectureId: dbms.id }` (the two swap places) |
| Permanent change | `{ type: "PERMANENT_CHANGE", lectureId: oop.id, target: slot(thursday, "09:00", "10:00", oop.roomId) }`, then `submit` to `demo-admin` as `HOD`; only the HOD's approval applies it, to every remaining Monday |

**Undo it**: `await tt.post(`/api/v1/tickets/${applied.id}/revert`, { reason: "No longer needed" })` creates a revert
ticket; apply it and the lecture is back (a cancelled one too). The original shows `REVERTED`.

**Notifications** (as the person who should have been told): `await tt.get("/api/v1/notifications")`,
`/api/v1/notifications/unread-count`, `POST /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`.
Applying a change tells the author, the faculty involved, the HOD, the CR and the batch students.

**Issues** need a class representative. As `demo-admin` give `demo` the CR role of the division, then reload `demo`'s window:

```js
const roles = (await tt.get("/api/v1/roles?size=100")).items;
await tt.post(`/api/v1/users/${demoCampus.demoUserId}/scoped-roles`, { roleId: roles.find(r => r.name === "CR").id, scopeId: demoCampus.division });
```

As `demo`: `await tt.post("/api/v1/issues", { sessionType: "THEORY", divisionId: "<division id>", title: "Extra DBMS lecture", mentionedFacultyIds: ["<Dr Admin faculty id>"] })`.
`demo-admin` gets an `ISSUE_MENTION` notification, sees the issue, links a ticket (`POST /api/v1/issues/{id}/links`) and
closes it (`POST /api/v1/issues/{id}/close`). Other roles to try the same way: HOD, BR (a batch id), Student (a batch id).
Remove a role again with `DELETE /api/v1/users/{id}/scoped-roles/{roleId}?scopeId=...`.

**Look underneath**: Adminer (<http://localhost:8081>) shows `timetable_change_ticket`, `ticket_change_item`,
`ticket_review`, `lecture_occurrence` (the `effective_*` columns are the live state, the others the base timetable),
`occurrence_claim` (the rows that make double-booking impossible), `issue`, `notification`, `audit_entry`. The **Audit** page
of the web app lists every ticket and issue action.

## 6. The automated tests (no apps needed, Docker is)

```powershell
Set-Location backend
.\mvnw.cmd -B '-Dmaven.compiler.release=21' verify     # drop the release flag if you have JDK 25
```

About 770 tests; it takes 10 to 15 minutes. Quote each `-D` in PowerShell. Run it from `backend`.

## 7. Stop and start over

`Ctrl+C` stops the apps. `pnpm infra:down` stops the containers. `pnpm reset` wipes the database and Keycloak (it keeps `.env`),
after which you repeat steps 2 to 4.
