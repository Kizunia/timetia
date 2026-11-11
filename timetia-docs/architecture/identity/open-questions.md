# Open questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

Decisions about authentication and authorization that the documents and the code do not settle. Each has a recommendation, which is a proposal and not a decision. Questions that block a work package are first.

Questions already decided are in the [decisions](../../decisions/INDEX.md). Questions about ticket business rules that are not identity or access are in the [module's open questions](../../modules/lecture-rescheduling/open-questions.md).

## Blocking Phase 2

### OQ-A1. Who owns the department, division and batch data?

- **Why it matters:** scoped roles point at a department, division or batch, and scope includes children. Something must hold that tree and say whether a scope exists.
- **Depends on it:** W2.1, W2.3, W2.7.
- **Options:** the reference-data owner among the other developers owns the tables and implements the scope directory port; or security owns a minimal table filled by import.
- **Recommendation:** the reference-data owner owns the tables, because the timetable needs them too. Security defines the port and uses a fake until it exists. If nobody owns them yet, name an owner now.

### OQ-A2. Can a person hold several roles and scopes, and do CR and BR roles expire?

- **Why it matters:** a faculty member may also be an HOD. A student may also be a CR. A CR or BR may serve for one term only. The model must allow several assignments, and "expires" needs a validity date.
- **Depends on it:** W2.1 (schema), W2.3.
- **What the documents say:** a class can have several CRs. Nothing says whether roles expire. A data model fix (F-G2) suggests ending roles at term end, but no rule says so.
- **Recommendation:** allow several assignments per person. Add an optional end date. A person acts with the union of their assignments, each limited to its own scope.

### OQ-A3. Who decides a cross-department case?

- **Why it matters:** a faculty member of one department may teach a division of another, and rooms have an owning department. The documents say an HOD sees "the department" and Super HOD works across departments, but the original spec's "Super HOD approves cross-department cases" did not reach the module rules.
- **Depends on it:** the HOD and Super HOD rows of the permission matrix; W2.4, W2.6.
- **Recommendation:** an HOD's scope is the department of the **lecture's division**. Super HOD handles anything else. Confirm with the product owner.

### OQ-A4. Are Faculty and Student roles, or only profiles?

- **Why it matters:** the data model fix F-G2 removes Faculty and Student from the role list and keeps them as profile tables. In the inherited design **permissions come only from roles**. If Faculty is only a profile, faculty would need permissions from somewhere else.
- **Depends on it:** W2.2 and the seed.
- **Recommendation:** create `Faculty` and `Student` as role templates **and** keep the profiles. The role carries the permissions, the profile supplies the relation ("lectures they teach", "their batch"). Fix F-G2 gets a note.

### OQ-A5. Who may revert a ticket that is not an exchange or a substitute?

- **Why it matters:** the revert rule names the author, the affected faculty and the HOD, but its text speaks of exchanges and substitutes. For a plain reschedule, an extra lecture or a permanent change, only the HOD is certain.
- **Depends on it:** the `revert` row of the matrix; W2.6.
- **Recommendation:** the author may revert their own ticket of any type before the lecture starts, and an HOD may revert any in the department. Permanent changes stay with the HOD.

### OQ-A14. Where does scope storage live?

- **Why it matters:** extending Kibootia's access tables is simpler and safer, but edits a Kibootia module. A separate table leaves it alone but creates two sources of assignments. See the [target architecture](target-architecture.md).
- **Depends on it:** W2.1 and ADR PLAT-0004.
- **Recommendation:** extend the access tables (option A) and keep the change small and separable so it can be offered to Kibootia.

## Blocking later work

### OQ-A8. Should self-registration stay on in the Timetia realm?

- **Why it matters:** Kibootia ships with it on. Timetia's users are seeded. Open registration creates accounts nobody expects ([hardening backlog](hardening-backlog.md), R4).
- **Depends on it:** W1.6. [ADR 0001](../../decisions/0001-authentication-follows-kibootia.md) follows Kibootia and does not settle this.
- **Recommendation:** turn it off in the Timetia realm. Users arrive by import and invitation. In any case, keep Timetia permissions out of the default role.

### OQ-A9. How does the platform Administrator role relate to Super Admin?

- **Why it matters:** the inherited Administrator role holds every platform permission and runs user and role administration. The documents' Super Admin only configures rooms, slots and the calendar. [ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md) gives Super Admin role assignment.
- **Depends on it:** W3.3 and the seed.
- **Recommendation:** Super Admin is **one role template that includes Administrator's platform permissions**, and the first Super Admin is the bootstrap administrator. No second top role.

### OQ-A10. How do students get accounts, and from where do their email addresses come?

- **Why it matters:** students are read-only users who see the timetable and notifications, so each needs a Keycloak account with an email address. The module's data list says contacts are not in the export. Inviting thousands of students by email is a real operation.
- **Depends on it:** W3.1, W3.4.
- **Options:** import with college emails and invite everyone; or create accounts on demand at first contact with a trusted sign-in; or defer student accounts and ship faculty, HOD and representatives first.
- **Recommendation:** ask the product owner which sources of student email exist. Until then plan the import for staff and representatives, and design the format so students can be added.

## Not blocking

### OQ-A6. What happens when an HOD is absent?

No delegate or acting HOD is documented, and one HOD per department is recorded. **Recommendation:** none in version 1. A Super HOD acts. Revisit if it becomes a real problem.

### OQ-A7. Must a CR or BR approve, or only acknowledge?

The data model has an acknowledged status that does not block. The rules do not say whether a requested CR or BR approval is required before a change applies. This belongs to the ticket module. **Recommendation:** treat it as the ticket module's decision. Authorization only tells whether they may.

### OQ-A11. Who sees tickets, and who reads the audit log?

Not defined: ticket visibility for a non-involved faculty member, for Super HOD and for Super Admin, the right to read the system-wide audit log, and its retention. **Recommendation:** involved people and department scope see tickets; Super Admin and Super HOD read the log; keep Kibootia's 365-day default until the owner chooses.

### OQ-A12. HOD-authored permanent changes and self-approval by HOD or Super HOD

Who raises a permanent change, whether an HOD's own permanent change needs a second approver, and whether HOD or Super HOD may self-approve are not defined. **Recommendation:** the HOD raises and approves their department's permanent changes; a Super HOD approves an HOD's own. Confirm.

### OQ-A13. How far does the rename go?

The Java package root is documented by Kibootia as fixed (`com.kibootia.backend`). Realm, clients, cookies, headers, environment variable names and page text can change. **Recommendation:** rename everything a user, an operator or a log can see. Keep the package root, as Kibootia documents it, and note it in the handover.

## Document repairs

Documents that disagree with each other or with a decision. Planned as W4.5, not edited now.

| Document | Problem | Fix |
| --- | --- | --- |
| [data model fixes](../../research/data-model-fixes.md), F-A2 | Proposes a local password and an auth provider, which [ADR 0001](../../decisions/0001-authentication-follows-kibootia.md) does not adopt | Add a pointer to the decision |
| [data model fixes](../../research/data-model-fixes.md), F-G2 | Removes Faculty and Student from roles, subject to OQ-A4 | Note the outcome |
| [Super Admin](../../shared/roles/super-admin.md) and [roles and powers](../../modules/lecture-rescheduling/rules/permissions.md) | Do not mention user and role administration, which [ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md) assigns. Dated 2026-10-27 yet contain later changes | Update after the work exists |
| [rules index](../../modules/lecture-rescheduling/rules/README.md) and the module changelog | Describe shared courses as skipped. The shared-courses rule and a later changelog entry enable them | Align |
| [ticket lifecycle](../../modules/lecture-rescheduling/rules/ticket-lifecycle.md) | Says student ticket visibility is open. The module's open questions say results only | Align |
| [archived spec](../../modules/lecture-rescheduling/_archive/original-spec.md) versus [conflict rules](../../modules/lecture-rescheduling/rules/conflict-rules.md) | The spec lets Super Admin override a ticket. The rules say nobody overrides a conflict | The rules win, and the archive says so |
| [STRUCTURE.md](../../STRUCTURE.md) | Describes folders and files that do not exist (`product/`, `engineering/`, a permissions matrix, a README) | Update or create them as the migration proceeds |
