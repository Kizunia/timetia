# Decisions

Project-wide ADRs. Module ADRs live in each module and are all listed in [INDEX.md](INDEX.md).

| File | Summary |
| --- | --- |
| [0001-authentication-follows-kibootia.md](0001-authentication-follows-kibootia.md) | Keycloak is the only credential store, as in Kibootia; no local passwords |
| [0002-scoped-roles-on-the-current-kibootia-contract.md](0002-scoped-roles-on-the-current-kibootia-contract.md) | A small scoped-role layer on the current contract; Kibootia's uncommitted drafts are context, not a constraint |
| [0003-role-administration-by-import-and-super-admin.md](0003-role-administration-by-import-and-super-admin.md) | Users and scoped assignments are imported; Super Admin adjusts them; no delegation in version 1 |
| [0004-scoped-assignments-and-policy-decided-permissions.md](0004-scoped-assignments-and-policy-decided-permissions.md) | Scope stored in the access tables; policy-decided permissions that flat checks never satisfy; Super Admin is the Administrator role |
| [0005-identity-hardening-decisions.md](0005-identity-hardening-decisions.md) | Registration off, refusals audited, token type checked, production guard, rename depth |
| [0006-provisioning-and-bootstrap.md](0006-provisioning-and-bootstrap.md) | Add-only CSV import by Super Admin with places named by code; the application invites the first Super Admin at startup |
| [0007-users-cannot-edit-their-own-email.md](0007-users-cannot-edit-their-own-email.md) | The realm lets only an administrator change a user's email and username, because the address is the identity; proposed, not yet confirmed |
