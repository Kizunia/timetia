# Provisioning people and roles

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

How to get the first Super Admin, load people with their roles from a file, and fix mistakes. It follows [ADR 0006](../decisions/0006-provisioning-and-bootstrap.md) and is written for the person who runs the system, a Super Admin.

**Status of this runbook.** On 2026-11-12 its author followed the first-Super-Admin, import, repeat-import and recovery steps against a running local stack (real Keycloak, MySQL and Mailpit; see [testing and verification](../architecture/identity/testing-and-verification.md)), and corrected it where it was wrong. **Nobody other than its author has followed it yet**, which the [implementation plan](../architecture/identity/implementation-plan.md) (W3.4) asks for, so it is not marked done. The steps for a production environment (a real mail server, a production Keycloak) have not been run at all, because neither exists yet. **Roles that need a department, division or batch cannot be given yet**: nothing in the application provides those places ([what that means](#what-does-not-work-yet)).

## The two ideas to hold on to

- **An import only adds.** It creates people who have no account and gives roles that are missing. It never removes, changes, disables or re-enables anything. Sending the same file twice changes nothing. So when something is wrong, you correct the file and send it again.
- **Check before you import.** "Check the file" shows what every row would do and writes nothing. Import only after it looks right.

## The first Super Admin

A fresh environment has no administrator, and self-registration is off, so nobody can invite anybody. The application deals with this itself.

1. Set the address of the person who will be Super Admin, in the backend's environment:
   `KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL=name@college.example` (the setting is `kibootia.identity.bootstrap-admin-email`). Locally this is a line in the root `.env`, which compose passes to the backend. **Changing `.env` needs the backend container recreated** (`docker compose up -d backend`); `docker restart` keeps the old value.
2. Start the application. If nobody holds the administrator role, it creates an account for that address in the identity provider and sends the invitation, as it would for any invited person. The log says so (a warning: "No administrator exists, so the bootstrap administrator was invited") and the audit log gets `identity.bootstrap-admin.invited`. Locally the mail is in Mailpit at http://localhost:8025.
3. That person follows the link in the email, sets a password and signs in. At that first sign-in with the verified address they become Super Admin, and the audit log gets `identity.bootstrap-admin.granted`.
4. After that the setting does nothing, whatever it says. You may clear it.

What to know:

- **Only that address, only while there is no administrator.** Anyone else who signs in is an ordinary user, and so is the same person if they sign in with a different or unverified address.
- **The link expires** after 7 days (`kibootia.identity.link-lifespan`). If that person has not signed in and there is still no administrator, **restart the backend**: it sends the link again and the audit log gets `identity.bootstrap-admin.invitation-resent`. Each restart sends one more mail, and the newest link is the one to follow. Once an administrator exists a restart sends nothing, whatever the setting says. (All three were observed: one mail at the first start, a second after one restart, none after the person became administrator.)
- **If the identity provider is down at startup**, the application starts anyway and logs that it was not invited. Start it again when the identity provider is up.
- **If the identity provider already has an account for that address** (the local demo administrator, or an account made by hand), nothing is created. That person becomes Super Admin when they sign in, by the same rule. An account made by hand must have its email marked verified.
- Locally, `demo-admin@timetia.localhost` is already seeded, so nothing is sent. Only the configured address becomes Super Admin: if the setting names another address, `demo-admin` signs in as an ordinary user (observed).
- **To start a local environment again with no administrator**, remove its volumes (`docker compose down -v` removes the database, Keycloak's data and the mail), set the address, and start it. Do not do this to an environment with real data.

To check it worked: sign in as that person. The Administration menu is there, with an Import section, and the audit log has `identity.bootstrap-admin.granted`.

## The file

A CSV file, UTF-8, with a header line. One row for each role a person holds. In a spreadsheet, save as **CSV UTF-8**.

```csv
email,role,scope
anita.rao@college.example,HOD,CSE
ravi.kulkarni@college.example,Faculty,
sneha.patil@college.example,CR,CSE-A
sneha.patil@college.example,Student,CSE-A1
```

| Column | What goes in it |
| --- | --- |
| `email` | The person's address. Upper and lower case are the same address. The same address on several rows is one person. |
| `role` | The name of a role, as the Roles page shows it. |
| `scope` | The **code** of the department, division or batch the role applies to. Empty for a role that applies everywhere. |

The columns may come in any order, and `scope` may be left out when no row needs one. Values may be in quotes if they contain a comma. Blank lines are ignored.

Which roles a file can give:

| Role | Goes with | `scope` |
| --- | --- | --- |
| Super HOD | everywhere | empty |
| Faculty | everywhere | empty |
| HOD | one department | the department's code, for example `CSE` |
| CR | one division | the division's code, for example `CSE-A` |
| BR | one batch | the batch's code, for example `CSE-A1` |
| Student | one batch | the batch's code |

A file **cannot** give Super Admin (the Administrator role), the `User` role or any role an administrator made by hand without a place. Those are refused row by row. Super Admin is given on the user's page, by an existing Super Admin.

Codes are matched exactly as written. A code must name one place; a plain `A` that exists in several departments cannot be used, so the places need codes such as `CSE-A`. Who maintains the codes is the owner of department, division and batch data.

**Size.** At most 1000 rows and 512 KB in one file. For a bigger intake, split it, for example by department. Splitting is safe because a repeat only adds.

## Running an import

1. Sign in as Super Admin and open **Administration → Import**.
2. Choose the file and press **Check the file**. Nothing is written and no email is sent.
3. Read the table. Every row says what it would do. See [reading the report](#reading-the-report). If a row has an error, none of that person's rows is applied; correct the file and check again.
4. When it is right, press **Import**. Each new person is emailed an invitation.
5. Read the report again. It is the record of what was done. The audit log also has one `identity.user-import.completed` entry for the run, with counts, and an entry for each invitation and each role given.

Only one real import runs at a time, and at most ten an hour. If you are told another import is running, wait for it.

## Reading the report

Every row has a result in words:

| Result | Meaning |
| --- | --- |
| Added / Would add | The role was given (or would be). A "new person" note means they had no account and were invited. |
| Already there | The person already had this role in this place. This is what you see when you send a file again. |
| Skipped | Left alone on purpose. `duplicate_in_file`: the same role and place appear on an earlier line. `person_has_errors`: another row of this person has an error. |
| Error | The row cannot be used. The code says why (below). |
| Not processed | The import did not reach it. Send the same file again. |

Error codes:

| Code | What to do |
| --- | --- |
| `invalid_email`, `email_required`, `role_required` | Correct the cell. |
| `wrong_field_count` | The row has more or fewer values than the header. Usually an unquoted comma. |
| `unknown_role` | No role has that name. Check the Roles page for the exact name. |
| `role_not_assignable` | A platform role (Administrator, User, a custom role). A file cannot give it. |
| `scope_required` | This role needs the code of a department, division or batch. |
| `scope_not_allowed` | This role applies everywhere. Leave `scope` empty. |
| `unknown_scope` | No place of the right kind has that code. A division code in a department role is this error. |
| `scope_unvalidated` | The application cannot check places yet. See [what does not work yet](#what-does-not-work-yet). |
| `account_disabled`, `account_deleted` | The person's account is disabled or deleted. An import never changes that. Enable or restore them on the Users page first. |
| `email_ambiguous` | More than one account has this address. Resolve it before importing. |
| `account_without_profile` | The identity provider has an account for this address that this application does not know. See [below](#when-something-goes-wrong). |
| `identity_unavailable` | The identity provider could not be reached. Send the file again later. After five people in a row the rest are "Not processed". |
| `time_budget_exceeded` | The import ran out of its two minutes. Send the file again. |
| `internal_error` | Something unexpected. Nothing was changed for this person. Send it again; if it repeats, give the support person the time of the run. |

A refused file (nothing is read) gives one message instead of a table: not UTF-8, a quote that is never closed, an unknown or missing column, no rows, too many rows, or too big.

## Fixing mistakes

- **A wrong cell, found before importing.** Correct the file and check again.
- **A wrong cell, found after importing.** An import cannot be undone, only corrected. Correct the file and send it again to add what was missing. Then take away what was wrong on the user's page, **Roles in a place**, with **Remove**, one assignment at a time. A role given by mistake has been in force since the import; roles apply from the next request.
- **A person invited with a wrong address.** Disable them on the Users page. The invitation goes to the wrong mailbox, so also correct the file and import the right address, which creates a second person. A person cannot change their own address in Keycloak's account console: only an administrator can, in Keycloak's admin console ([ADR 0007](../decisions/0007-users-cannot-edit-their-own-email.md), proposed). Their address in Timetia follows the token at their next sign-in.
- **A person who should lose a role.** Remove it on their page. A file never removes.

## Onboarding a term

1. Prepare one file for the new term, with the new CRs, BRs and students and any new faculty and HODs. People who already have an account and keep their roles may stay in the file: they show as "Already there".
2. Check, fix and import it as above.
3. **Take away what has ended.** Last term's CRs and BRs keep their roles until someone removes them, because an import never removes. Remove them on their pages. There is no end date on a role yet ([OQ-A2](../architecture/identity/open-questions.md)).
4. For a large intake, send the files one after another and check each report.

## When something goes wrong

**`account_without_profile` for an address.** The identity provider has an account that this application has no profile for. This happens when an earlier row failed and the account could not be removed again, or when someone made the account by hand. The person cannot be invited again until it is resolved.

1. In the identity provider's admin console (locally http://localhost:8180, realm `timetia`, **Users**), find the user by email.
2. If it was made by a failed import (no password set, no one has signed in with it), delete it, then send the file again. (Reproduced on 2026-11-12 by creating such an account by hand: the import reported `account_without_profile`; after the account was deleted the same file added the role and sent one invitation, and sending it a third time reported "Already there".)
3. If it is a real account someone made by hand, ask that person to sign in once. The application then creates their profile, and the next import adds their roles.

**`Another import is running`.** Wait for the other to finish and try again. If none is running, the lock frees itself within about four minutes.

**Too many requests.** The real-import limit is ten an hour. Wait.

**Invitation emails do not arrive.** They are sent by the identity provider after the import commits, with retries. If the identity provider's mail is down, the person exists and the email is delayed, not lost; a delivery that keeps failing is listed by the API at `/api/v1/failed-events`, where an administrator can retry it. A person who has not accepted can also be sent the link again from their page on the Users list.

## Settings

All are optional; these are the defaults.

| Setting | Default | What it does |
| --- | --- | --- |
| `KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL` (`kibootia.identity.bootstrap-admin-email`) | empty | The address that is invited and made Super Admin while there is no administrator |
| `kibootia.identity.bootstrap-invite.enabled` | `true` | Turns the startup invitation off; the first sign-in rule still applies |
| `kibootia.identity.import.max-rows` | 1000 | Rows in one file |
| `kibootia.identity.import.max-bytes` | 524288 | Bytes in one file |
| `kibootia.identity.import.time-budget` | `PT2M` | How long a real import may run |
| `kibootia.identity.import.max-consecutive-unavailable` | 5 | People in a row that may fail on the identity provider before the rest are not processed |
| `kibootia.rate-limit.limits.imports.capacity`, `.period` | 10, `PT1H` | Real imports per Super Admin |

## What does not work yet

- **Roles that need a place: HOD, CR, BR, Student.** The application has no department, division and batch data, so it cannot check a code. Rows for these roles come back `scope_unvalidated`, and nobody is invited for them. Super HOD and Faculty work. This stays until the owner of department, division and batch data implements the `ScopeDirectory` port, including the lookups by code ([integration contract](../architecture/identity/integration-contract.md)).
- **A check does not ask the identity provider.** It cannot know an address the identity provider has without a profile here; that shows up in the real import as `account_without_profile`.
- **No end date on a role.** Roles are removed by hand.
- **Students and email.** Every new student gets an invitation, so their addresses must be in the file. There is no on-demand creation at first contact.
- **A person cannot see their own roles in a place.** Their profile page lists platform roles only (`User`, `Administrator`). To know what someone holds, open their page on **Administration → Users**, section **Roles in a place**. Nothing in the running application yet shows a person what they may do, because no module that uses these roles exists.
