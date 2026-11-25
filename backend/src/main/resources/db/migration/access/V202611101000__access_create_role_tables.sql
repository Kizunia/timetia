-- Roles, what each role allows, and who holds which role (ADR 0025, ADR 0044). Permissions are defined in code; only
-- their names are stored here, so a permission a module no longer declares is ignored rather than breaking a role.

CREATE TABLE role (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    -- 1 while the role is live and NULL once deleted (ADR 0034), so a deleted role's name can be used again.
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(255) NOT NULL,
    -- ADMIN or USER for the two roles the foundation creates and protects; NULL for roles administrators create.
    built_in      VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_role_name (tenant_id, name, active_marker),
    -- NULLs are distinct, so this only limits the built-in roles: one live ADMIN and one live USER per tenant.
    UNIQUE KEY uq_role_built_in (tenant_id, built_in, active_marker)
);

-- Hard-deleted when a permission is taken off a role: the audit log keeps the history (ADR 0034 exception, ADR 0050).
CREATE TABLE role_permission (
    tenant_id  BINARY(16)   NOT NULL,
    role_id    BINARY(16)   NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission)
);

-- One row per user and role. The user's ID is the Keycloak ID; there is no foreign key because the user profile belongs
-- to another module (ADR 0039), and erasing a user removes these rows explicitly. Hard-deleted like role_permission.
CREATE TABLE role_assignment (
    tenant_id   BINARY(16)  NOT NULL,
    user_id     BINARY(16)  NOT NULL,
    role_id     BINARY(16)  NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    assigned_by BINARY(16)  NOT NULL,
    PRIMARY KEY (tenant_id, user_id, role_id),
    INDEX role_assignment_by_role_idx (tenant_id, role_id)
);

-- The permissions the built-in user role has already been offered. A permission a module adds later is given to the
-- user role once; an administrator who takes it off stays respected after the next start.
CREATE TABLE role_permission_seed (
    tenant_id  BINARY(16)   NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (tenant_id, permission)
);
