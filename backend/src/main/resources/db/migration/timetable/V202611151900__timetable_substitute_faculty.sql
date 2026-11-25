-- Phase C, slice 3: a substitute. The faculty member who teaches a lecture on its date can be changed by a ticket, as the
-- slot can. Like the slot, the faculty member is kept twice: the base column (faculty_id, from the teaching assignment) never
-- changes, and effective_faculty_id is who teaches it now. Everything that asks who teaches a lecture, the claims on the
-- teacher's time, the live timetable and who may see and change the lecture, reads the effective one.

ALTER TABLE lecture_occurrence
    ADD COLUMN effective_faculty_id BINARY(16) NULL;

UPDATE lecture_occurrence
SET effective_faculty_id = faculty_id;

ALTER TABLE lecture_occurrence
    MODIFY effective_faculty_id BINARY(16) NOT NULL;

DROP INDEX lecture_occurrence_by_faculty_idx ON lecture_occurrence;

CREATE INDEX lecture_occurrence_by_faculty_idx
    ON lecture_occurrence (tenant_id, effective_faculty_id, record_status, effective_date);
