# Decisions

Project-wide ADRs. Module ADRs live in each module and are all listed in [INDEX.md](INDEX.md).

| File | Summary |
| --- | --- |
| [0001-authentication-follows-kibootia.md](0001-authentication-follows-kibootia.md) | Keycloak is the only credential store, as in Kibootia; no local passwords |
| [0002-scoped-roles-on-the-current-kibootia-contract.md](0002-scoped-roles-on-the-current-kibootia-contract.md) | A small scoped-role layer on the current contract; Kibootia's uncommitted drafts are context, not a constraint |
| [0003-role-administration-by-import-and-super-admin.md](0003-role-administration-by-import-and-super-admin.md) | Users and scoped assignments are imported; Super Admin adjusts them; no delegation in version 1 |
