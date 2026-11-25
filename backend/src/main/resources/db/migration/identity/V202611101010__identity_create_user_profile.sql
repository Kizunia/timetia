-- The user profile (ADR 0025, ADR 0041): keyed by the Keycloak user ID, created the first time the user is seen. Name and
-- email are a read copy of what Keycloak says, refreshed from the token; Kibootia owns the status and the preferences.
-- Soft-deleted like every business table (ADR 0034); erasing a user removes the row for good.
CREATE TABLE user_profile (
    id                   BINARY(16)   NOT NULL,
    tenant_id            BINARY(16)   NOT NULL,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           BINARY(16)   NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    updated_by           BINARY(16)   NOT NULL,
    deleted_at           DATETIME(6)  NULL,
    -- INVITED, ACTIVE or DISABLED. Being deleted is the separate fact deleted_at.
    status               VARCHAR(20)  NOT NULL,
    username             VARCHAR(255) NULL,
    email                VARCHAR(320) NULL,
    first_name           VARCHAR(255) NULL,
    last_name            VARCHAR(255) NULL,
    email_verified       BOOLEAN      NOT NULL,
    -- SYSTEM, LIGHT or DARK.
    theme                VARCHAR(20)  NOT NULL,
    -- The issue time of the token the name and email were last copied from, so an older token cannot undo a newer copy.
    name_synced_at       DATETIME(6)  NOT NULL,
    -- Set when erasing a deleted user begins, so the retention job does not start it again.
    erasure_requested_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX user_profile_by_status_idx (tenant_id, status),
    INDEX user_profile_by_email_idx (tenant_id, email),
    INDEX user_profile_by_deleted_idx (deleted_at)
);
