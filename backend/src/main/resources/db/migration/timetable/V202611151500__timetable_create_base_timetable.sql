-- Phase B: the base timetable. A division has timetable versions; a version holds weekly entries; publishing a version
-- generates the dated occurrences the live timetable shows. The version in force on a date is the PUBLISHED version of the
-- division with the greatest effective_from not after that date (data model F-F1).

CREATE TABLE timetable_version (
    id             BINARY(16)  NOT NULL,
    tenant_id      BINARY(16)  NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    created_by     BINARY(16)  NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    updated_by     BINARY(16)  NOT NULL,
    division_id    BINARY(16)  NOT NULL,
    term_id        BINARY(16)  NOT NULL,
    effective_from DATE        NOT NULL,
    status         VARCHAR(12) NOT NULL,
    -- effective_from while PUBLISHED and NULL otherwise, so two published versions of a division cannot start on the
    -- same day while drafts may (MySQL treats NULLs as distinct in a unique key).
    published_from DATE GENERATED ALWAYS AS (IF(status = 'PUBLISHED', effective_from, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_timetable_version_published (tenant_id, division_id, published_from),
    INDEX timetable_version_by_division_idx (tenant_id, division_id, status),
    CONSTRAINT ck_timetable_version_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE timetable_entry (
    id                   BINARY(16)   NOT NULL,
    tenant_id            BINARY(16)   NOT NULL,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           BINARY(16)   NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    updated_by           BINARY(16)   NOT NULL,
    timetable_version_id BINARY(16)   NOT NULL,
    assignment_id        BINARY(16)   NOT NULL,
    day_of_week          TINYINT      NOT NULL,
    start_time           TIME         NOT NULL,
    end_time             TIME         NOT NULL,
    room_id              BINARY(16)   NULL,
    mode                 VARCHAR(10)  NOT NULL,
    meeting_link         VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX timetable_entry_by_version_idx (tenant_id, timetable_version_id),
    -- Monday to Saturday, on the default 08:00 to 20:00 grid (data model F-E1, F-E2).
    CONSTRAINT ck_timetable_entry_day CHECK (day_of_week BETWEEN 1 AND 6),
    CONSTRAINT ck_timetable_entry_time CHECK (start_time < end_time AND start_time >= '08:00:00' AND end_time <= '20:00:00'),
    -- An in-person lecture has a room and an online one has none (data model F-D3).
    CONSTRAINT ck_timetable_entry_mode CHECK (
        (mode = 'IN_PERSON' AND room_id IS NOT NULL) OR (mode = 'ONLINE' AND room_id IS NULL))
);

CREATE TABLE lecture_occurrence (
    id                   BINARY(16)   NOT NULL,
    tenant_id            BINARY(16)   NOT NULL,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           BINARY(16)   NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    updated_by           BINARY(16)   NOT NULL,
    timetable_entry_id   BINARY(16)   NOT NULL,
    timetable_version_id BINARY(16)   NOT NULL,
    assignment_id        BINARY(16)   NOT NULL,
    occurrence_date      DATE         NOT NULL,
    start_time           TIME         NOT NULL,
    end_time             TIME         NOT NULL,
    -- Copied from the teaching assignment, none of which can change: where the lecture is, what kind it is, who teaches it.
    -- What can change (a faculty member's account, the batches of a division) is looked up when it is read.
    department_id        BINARY(16)   NOT NULL,
    division_id          BINARY(16)   NOT NULL,
    batch_id             BINARY(16)   NULL,
    session_type         VARCHAR(10)  NOT NULL,
    faculty_id           BINARY(16)   NOT NULL,
    room_id              BINARY(16)   NULL,
    mode                 VARCHAR(10)  NOT NULL,
    meeting_link         VARCHAR(500) NULL,
    -- ACTIVE, or SUPERSEDED once a version that takes effect earlier replaces it (data model F-J3).
    record_status        VARCHAR(12)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lecture_occurrence_entry_date (tenant_id, timetable_entry_id, occurrence_date),
    INDEX lecture_occurrence_by_date_idx (tenant_id, record_status, occurrence_date),
    INDEX lecture_occurrence_by_division_idx (tenant_id, division_id, record_status, occurrence_date),
    INDEX lecture_occurrence_by_faculty_idx (tenant_id, faculty_id, record_status, occurrence_date),
    INDEX lecture_occurrence_by_room_idx (tenant_id, room_id, record_status, occurrence_date),
    CONSTRAINT ck_lecture_occurrence_status CHECK (record_status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_lecture_occurrence_mode CHECK (
        (mode = 'IN_PERSON' AND room_id IS NOT NULL) OR (mode = 'ONLINE' AND room_id IS NULL))
);
