-- Phase A, slice 3: the reference data the base timetable needs beyond the campus structure — subjects, rooms, faculty
-- and academic terms. Same conventions as the campus tables (BINARY(16) UUIDv7 key, tenant, version, audit columns, soft
-- delete with the generated active-marker column, and a code unique per kind within a tenant).

CREATE TABLE subject (
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
    name          VARCHAR(150) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subject_code (tenant_id, code, active_marker)
);

CREATE TABLE room (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    code          VARCHAR(20)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    room_type     VARCHAR(20)  NOT NULL,
    capacity      INT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_room_code (tenant_id, code, active_marker)
);

CREATE TABLE faculty (
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
    name          VARCHAR(150) NOT NULL,
    department_id BINARY(16)   NOT NULL,
    -- The Keycloak user this faculty member signs in as; the lecture access facts expose it as a teaching-faculty user id.
    -- Nullable: a faculty record may be seeded before the person has an account.
    user_id       BINARY(16)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_faculty_code (tenant_id, code, active_marker),
    INDEX faculty_by_department_idx (tenant_id, department_id),
    INDEX faculty_by_user_idx (tenant_id, user_id)
);

CREATE TABLE academic_term (
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
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_term_code (tenant_id, code, active_marker)
);
