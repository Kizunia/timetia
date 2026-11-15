-- Phase A, slice 4: teaching assignments — who teaches which subject, to which division (theory) or batch (lab/tutorial),
-- as which session type. A base timetable entry places one of these in a slot. Follows the data model's rule (F-J1, F-D2):
-- a theory assignment names no batch; a lab or tutorial names exactly one. Uniqueness uses a generated batch-scope column
-- (the batch id, or a zero sentinel for theory) so MySQL's NULL-distinct behaviour does not let duplicate theory rows in.

CREATE TABLE teaching_assignment (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    division_id   BINARY(16)   NOT NULL,
    batch_id      BINARY(16)   NULL,
    subject_id    BINARY(16)   NOT NULL,
    faculty_id    BINARY(16)   NOT NULL,
    session_type  VARCHAR(20)  NOT NULL,
    -- The batch id for a lab/tutorial, or a zero sentinel for theory, so the unique key below also forbids a duplicate
    -- theory assignment (MySQL treats NULLs as distinct in a unique key).
    batch_scope   BINARY(16) GENERATED ALWAYS AS (IFNULL(batch_id, x'00000000000000000000000000000000')) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_teaching_assignment (tenant_id, division_id, subject_id, session_type, batch_scope, active_marker),
    INDEX teaching_assignment_by_division_idx (tenant_id, division_id),
    INDEX teaching_assignment_by_faculty_idx (tenant_id, faculty_id),
    CONSTRAINT ck_teaching_assignment_batch CHECK (
        (session_type = 'THEORY' AND batch_id IS NULL)
        OR (session_type IN ('LAB', 'TUTORIAL') AND batch_id IS NOT NULL))
);
