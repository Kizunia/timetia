# Trying everything by clicking

A click-through of what the app does, role by role. Starting, stopping, resetting, the seed and the list of accounts are in
[PRESENT.md](PRESENT.md) sections 1 to 3; in short, from the repository root in PowerShell:

```powershell
pnpm install
pnpm bootstrap     # once: writes .env with random secrets
pnpm infra:up      # Docker Desktop must be running
pnpm dev           # backend :8080, web app http://localhost:3470
pnpm seed          # in a second window, once the backend says "Started BackendApplication"
```

All accounts use `DEMO_USER_PASSWORD` from `.env`, except `demo-admin`, which uses `DEMO_ADMIN_PASSWORD`. Reviews need two
people at once, so use a normal window and a private window.

## Super Admin (`demo-admin`)

1. **Administration -> Campus -> Rooms**: create `R901`; rename it; try to create another `R901` (the message appears under
   Code); delete it.
2. **Campus -> Faculty**: use **Account** on a teacher to see how an account is linked or removed. Put it back as it was.
3. **Administration -> Timetables -> New version**: choose a division, the term `SEM-...` and a date inside the term that no
   published version of that division starts on. Add two lectures. Add a third that books the same teacher in the same
   hour and read the message. Remove or fix it, then **Publish**. The timetable shows the new week from that date.
4. **Administration -> Audit log**: the actions above are listed.

## Teacher (`demo`)

1. **Timetable**: tick "Only my lectures". Click a lecture that has not started -> **Change this lecture**.
2. **Reschedule** it: only free rooms are offered. Create -> **Apply** -> the lecture shows "Moved".
3. Raise another into an occupied slot: the ticket shows what is in the way and **Apply** is disabled. **Edit** the time.
4. Try **Cancel**, **Substitute**, **Exchange**, **Permanent change**, and **Tickets -> New extra lecture**.
5. On an applied ticket, **Revert** -> apply the revert: the lecture is back.

## Review (`demo` and `demo-admin`)

1. As `demo`, raise a reschedule and **Send for review** to the HOD.
2. As `demo-admin`, **Tickets -> To review** -> **Review** -> approve: the ticket is applied.
3. Do it again and **Reject** with a reason; as `demo`, edit and send again, or **Take to the HOD**.
4. The bell in the header counts what is new; **Notifications** lists it.

## Class representative (`cr.cse-a`) and mentioned teacher (`demo`)

1. As `cr.cse-a`: **Issues -> Open an issue** about theory for CSE-A and mention Dr Demo.
2. As `demo`: the bell shows the mention; open the issue; raise a ticket from the timetable; back on the issue use
   **Link a ticket**, then **Close the issue**.

## Batch representative and student (`stu.cse-a1`), and a plain student (`stu.me-a2`)

1. `stu.cse-a1` is both. **Issues -> Open an issue**, about **Lab**, for batch CSE-A1.
2. `stu.me-a2` sees only ME-A2's lectures, no "Change this lecture", and no Tickets or Issues links.
3. When a lecture of theirs is changed, their notification opens the **lecture** on the timetable, not the ticket.

## Look underneath

Adminer (<http://localhost:8081>) shows `timetable_change_ticket`, `ticket_review`, `lecture_occurrence`,
`occurrence_claim` (the rows that make double booking impossible), `issue`, `notification` and `audit_entry`. Every endpoint
is documented at <http://localhost:8090/actuator/swagger-ui>.

## Automated checks

```powershell
pnpm --filter kibootia-frontend test           # run from PowerShell
pnpm --filter kibootia-frontend test:e2e       # needs the stack running and seeded
Set-Location backend; .\mvnw.cmd -B verify     # needs Docker; 10 to 15 minutes
```
