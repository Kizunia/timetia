-- Phase A, slice 1: the campus structure (departments, divisions, batches). These are the places a Timetia role is
-- assigned in, so the reference module can provide the ScopeDirectory the access module asks before a scoped role is
-- given (Timetia ADR 0004, integration contract). Each table carries the foundation's conventions: BINARY(16) UUIDv7
-- key, tenant column, optimistic-lock version, audit columns and soft delete with the generated active-marker column,
-- so a deleted place's code can be used again while two live places of a kind cannot share a code within a tenant.

CREATE TABLE department (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_department_code (tenant_id, code, active_marker)
);

CREATE TABLE division (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    department_id BINARY(16)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_division_code (tenant_id, code, active_marker),
    INDEX division_by_department_idx (tenant_id, department_id)
);

CREATE TABLE batch (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    division_id   BINARY(16)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_code (tenant_id, code, active_marker),
    INDEX batch_by_division_idx (tenant_id, division_id)
);
