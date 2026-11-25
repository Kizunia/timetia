-- Phase C: a change to a lecture is laid over the base timetable, and every lecture holds what it needs for each hour.
--
-- The effective state of a lecture (data model OccurrenceEffectiveState) is kept in columns of the lecture's own row, beside
-- the base columns, which are never changed after the lecture is created: the base timetable stays as it was published and a
-- revert restores the effective columns from it. The row's version is the state revision a change is checked against.
--
-- A lecture holds its faculty member, its room, its students, for each hour it lasts (data model OccurrenceClaim). The unique
-- key is what makes "the first change applied wins" a guarantee of the database and not only a check in the application:
-- two changes that race for one slot cannot both be written.

ALTER TABLE lecture_occurrence
    ADD COLUMN effective_date         DATE         NULL,
    ADD COLUMN effective_start_time   TIME         NULL,
    ADD COLUMN effective_end_time     TIME         NULL,
    ADD COLUMN effective_room_id      BINARY(16)   NULL,
    ADD COLUMN effective_mode         VARCHAR(10)  NULL,
    ADD COLUMN effective_meeting_link VARCHAR(500) NULL,
    ADD COLUMN is_cancelled           BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN changed_by_ticket_id   BINARY(16)   NULL;

-- A lecture that exists before it is changed is exactly as the base timetable has it.
UPDATE lecture_occurrence
SET effective_date = occurrence_date,
    effective_start_time = start_time,
    effective_end_time = end_time,
    effective_room_id = room_id,
    effective_mode = mode,
    effective_meeting_link = meeting_link;

ALTER TABLE lecture_occurrence
    MODIFY effective_date       DATE        NOT NULL,
    MODIFY effective_start_time TIME        NOT NULL,
    MODIFY effective_end_time   TIME        NOT NULL,
    MODIFY effective_mode       VARCHAR(10) NOT NULL,
    ADD CONSTRAINT ck_lecture_occurrence_effective_mode CHECK (
        (effective_mode = 'IN_PERSON' AND effective_room_id IS NOT NULL)
        OR (effective_mode = 'ONLINE' AND effective_room_id IS NULL)),
    ADD CONSTRAINT ck_lecture_occurrence_effective_time CHECK (
        effective_start_time < effective_end_time AND effective_start_time >= '08:00:00'
        AND effective_end_time <= '20:00:00');

-- Lists and filters read the effective date; the base date is only read to find what a new version replaces.
DROP INDEX lecture_occurrence_by_date_idx ON lecture_occurrence;
DROP INDEX lecture_occurrence_by_division_idx ON lecture_occurrence;
DROP INDEX lecture_occurrence_by_faculty_idx ON lecture_occurrence;
DROP INDEX lecture_occurrence_by_room_idx ON lecture_occurrence;
CREATE INDEX lecture_occurrence_by_date_idx ON lecture_occurrence (tenant_id, record_status, effective_date);
CREATE INDEX lecture_occurrence_by_division_idx ON lecture_occurrence (tenant_id, division_id, record_status, effective_date);
CREATE INDEX lecture_occurrence_by_faculty_idx ON lecture_occurrence (tenant_id, faculty_id, record_status, effective_date);
CREATE INDEX lecture_occurrence_by_base_idx ON lecture_occurrence (tenant_id, division_id, record_status, occurrence_date);

-- Lectures created before this migration hold no claims, because the batches they were for are not known here. A
-- timetable published before it must be published again.
CREATE TABLE occurrence_claim (
    id            BINARY(16)  NOT NULL,
    tenant_id     BINARY(16)  NOT NULL,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BINARY(16)  NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    updated_by    BINARY(16)  NOT NULL,
    occurrence_id BINARY(16)  NOT NULL,
    claim_kind    VARCHAR(16) NOT NULL,
    resource_id   BINARY(16)  NOT NULL,
    claim_date    DATE        NOT NULL,
    cell_hour     TINYINT     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_occurrence_claim_cell (tenant_id, claim_kind, resource_id, claim_date, cell_hour),
    INDEX occurrence_claim_by_occurrence_idx (tenant_id, occurrence_id),
    CONSTRAINT ck_occurrence_claim_kind CHECK (claim_kind IN ('FACULTY', 'ROOM', 'DIVISION', 'STUDENT_BATCH')),
    CONSTRAINT ck_occurrence_claim_hour CHECK (cell_hour BETWEEN 8 AND 19)
);
