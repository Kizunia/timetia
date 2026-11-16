-- 03_foreign_keys.sql: foreign keys for the Timetia lab database (SE2004 DBMS, practicals 3 and 4: referential integrity).
--
-- Run after 01_schema.sql and 02_seed.sql. Adding the keys after the data is loaded also checks the data: a row that points
-- at nothing makes the ALTER TABLE fail.
--
-- The application's own schema (Flyway) has NO foreign keys, on purpose: Timetia is a modular monolith whose modules refer to
-- each other's rows by id only, so that a module can change its tables, or be taken out, without the database tying it to the
-- others, and the services check what they refer to. The lab asks for referential integrity, so this script adds it to the lab
-- database. It is not part of the application and is never run against it.
--
-- Not given a foreign key: the columns that hold a person's account id (author_user_id, reviewer_user_id, recipient_user_id,
-- user_id and the audit columns). The accounts live in the sign-in system, not in this database.
--
-- Rules used: a child row blocks the delete of its parent (RESTRICT, MySQL's default), except where a child has no life of its
-- own: the entries of a draft version go with the version, the changes of a ticket go with the ticket (CASCADE).

USE timetia_lab;

-- The campus ------------------------------------------------------------------------------------------------------------
ALTER TABLE division
    ADD CONSTRAINT fk_division_department FOREIGN KEY (department_id) REFERENCES department (id);
ALTER TABLE batch
    ADD CONSTRAINT fk_batch_division FOREIGN KEY (division_id) REFERENCES division (id);
ALTER TABLE faculty
    ADD CONSTRAINT fk_faculty_department FOREIGN KEY (department_id) REFERENCES department (id);

-- Who teaches what to whom ------------------------------------------------------------------------------------------------
ALTER TABLE teaching_assignment
    ADD CONSTRAINT fk_assignment_division FOREIGN KEY (division_id) REFERENCES division (id),
    ADD CONSTRAINT fk_assignment_batch    FOREIGN KEY (batch_id)    REFERENCES batch (id),
    ADD CONSTRAINT fk_assignment_subject  FOREIGN KEY (subject_id)  REFERENCES subject (id),
    ADD CONSTRAINT fk_assignment_faculty  FOREIGN KEY (faculty_id)  REFERENCES faculty (id);

-- The timetable --------------------------------------------------------------------------------------------------------------
ALTER TABLE timetable_version
    ADD CONSTRAINT fk_version_division FOREIGN KEY (division_id) REFERENCES division (id),
    ADD CONSTRAINT fk_version_term     FOREIGN KEY (term_id)     REFERENCES academic_term (id);

ALTER TABLE timetable_entry
    ADD CONSTRAINT fk_entry_version    FOREIGN KEY (timetable_version_id) REFERENCES timetable_version (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_entry_assignment FOREIGN KEY (assignment_id)        REFERENCES teaching_assignment (id),
    ADD CONSTRAINT fk_entry_room       FOREIGN KEY (room_id)              REFERENCES room (id);

-- A lecture refers to the entry that made it (none for an extra lecture a ticket added), to the room and the faculty member
-- as the base timetable has them and as they are now, and to the ticket whose change is laid over it.
ALTER TABLE lecture_occurrence
    ADD CONSTRAINT fk_lecture_entry        FOREIGN KEY (timetable_entry_id)   REFERENCES timetable_entry (id),
    ADD CONSTRAINT fk_lecture_version      FOREIGN KEY (timetable_version_id) REFERENCES timetable_version (id),
    ADD CONSTRAINT fk_lecture_assignment   FOREIGN KEY (assignment_id)        REFERENCES teaching_assignment (id),
    ADD CONSTRAINT fk_lecture_department   FOREIGN KEY (department_id)       REFERENCES department (id),
    ADD CONSTRAINT fk_lecture_division     FOREIGN KEY (division_id)          REFERENCES division (id),
    ADD CONSTRAINT fk_lecture_batch        FOREIGN KEY (batch_id)             REFERENCES batch (id),
    ADD CONSTRAINT fk_lecture_faculty      FOREIGN KEY (faculty_id)           REFERENCES faculty (id),
    ADD CONSTRAINT fk_lecture_eff_faculty  FOREIGN KEY (effective_faculty_id) REFERENCES faculty (id),
    ADD CONSTRAINT fk_lecture_room         FOREIGN KEY (room_id)              REFERENCES room (id),
    ADD CONSTRAINT fk_lecture_eff_room     FOREIGN KEY (effective_room_id)    REFERENCES room (id);

ALTER TABLE occurrence_claim
    ADD CONSTRAINT fk_claim_lecture FOREIGN KEY (occurrence_id) REFERENCES lecture_occurrence (id);

-- Tickets ----------------------------------------------------------------------------------------------------------------------
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT fk_ticket_assignment  FOREIGN KEY (assignment_id)        REFERENCES teaching_assignment (id),
    ADD CONSTRAINT fk_ticket_department  FOREIGN KEY (department_id)        REFERENCES department (id),
    ADD CONSTRAINT fk_ticket_division    FOREIGN KEY (division_id)          REFERENCES division (id),
    ADD CONSTRAINT fk_ticket_batch       FOREIGN KEY (batch_id)             REFERENCES batch (id),
    ADD CONSTRAINT fk_ticket_faculty     FOREIGN KEY (faculty_id)           REFERENCES faculty (id),
    ADD CONSTRAINT fk_ticket_acting      FOREIGN KEY (acting_faculty_id)    REFERENCES faculty (id),
    ADD CONSTRAINT fk_ticket_reverts     FOREIGN KEY (reverts_ticket_id)    REFERENCES timetable_change_ticket (id),
    ADD CONSTRAINT fk_ticket_reverted_by FOREIGN KEY (reverted_by_ticket_id) REFERENCES timetable_change_ticket (id);

ALTER TABLE ticket_change_item
    ADD CONSTRAINT fk_change_ticket      FOREIGN KEY (ticket_id)            REFERENCES timetable_change_ticket (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_change_source      FOREIGN KEY (source_occurrence_id) REFERENCES lecture_occurrence (id),
    ADD CONSTRAINT fk_change_created     FOREIGN KEY (created_occurrence_id) REFERENCES lecture_occurrence (id),
    ADD CONSTRAINT fk_change_assignment  FOREIGN KEY (teaching_assignment_id) REFERENCES teaching_assignment (id),
    ADD CONSTRAINT fk_change_faculty     FOREIGN KEY (faculty_id)           REFERENCES faculty (id),
    ADD CONSTRAINT fk_change_target_fac  FOREIGN KEY (target_faculty_id)    REFERENCES faculty (id),
    ADD CONSTRAINT fk_change_before_fac  FOREIGN KEY (before_faculty_id)    REFERENCES faculty (id),
    ADD CONSTRAINT fk_change_target_room FOREIGN KEY (target_room_id)       REFERENCES room (id),
    ADD CONSTRAINT fk_change_before_room FOREIGN KEY (before_room_id)       REFERENCES room (id);

ALTER TABLE ticket_review
    ADD CONSTRAINT fk_review_ticket     FOREIGN KEY (ticket_id)                 REFERENCES timetable_change_ticket (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_review_escalation FOREIGN KEY (escalated_from_review_id)  REFERENCES ticket_review (id);

ALTER TABLE conflict_check_run
    ADD CONSTRAINT fk_run_ticket FOREIGN KEY (ticket_id) REFERENCES timetable_change_ticket (id) ON DELETE CASCADE;

ALTER TABLE conflict_finding
    ADD CONSTRAINT fk_finding_run         FOREIGN KEY (run_id)                    REFERENCES conflict_check_run (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_finding_ticket      FOREIGN KEY (ticket_id)                 REFERENCES timetable_change_ticket (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_finding_lecture     FOREIGN KEY (occurrence_id)             REFERENCES lecture_occurrence (id),
    ADD CONSTRAINT fk_finding_other       FOREIGN KEY (conflicting_occurrence_id) REFERENCES lecture_occurrence (id),
    ADD CONSTRAINT fk_finding_other_ticket FOREIGN KEY (conflicting_ticket_id)    REFERENCES timetable_change_ticket (id);

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_ticket FOREIGN KEY (ticket_id) REFERENCES timetable_change_ticket (id) ON DELETE CASCADE;

-- The keys, listed from the data dictionary: the table, the column, and the table and column it points at.
SELECT table_name, column_name, referenced_table_name, referenced_column_name, constraint_name
FROM information_schema.key_column_usage
WHERE table_schema = 'timetia_lab' AND referenced_table_name IS NOT NULL
ORDER BY table_name, column_name;

-- Seeing referential integrity work ----------------------------------------------------------------------------------------------
-- 1. A parent cannot be deleted while a child needs it. Run this alone; it fails with
--      ERROR 1451 (23000): Cannot delete or update a parent row: a foreign key constraint fails
--    because SY-CSE-A still has batches, assignments and a timetable:
--      DELETE FROM division WHERE code = 'SY-CSE-A';
--
-- 2. A child cannot name a parent that does not exist. This fails with
--      ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails
--      INSERT INTO batch (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, division_id)
--      VALUES (UUID_TO_BIN(UUID()), UNHEX('00000000000070008000000000000001'), 0, NOW(6), UNHEX(REPEAT('0', 32)), NOW(6),
--              UNHEX(REPEAT('0', 32)), 'X1', 'No such division', UNHEX(REPEAT('f', 32)));

-- 3. A cascade: deleting a draft version takes its entries with it. Done in a transaction and rolled back, so the data stays.
SELECT COUNT(*) AS entries_of_the_draft FROM timetable_entry e JOIN timetable_version v ON v.id = e.timetable_version_id
WHERE v.status = 'DRAFT';

START TRANSACTION;
DELETE FROM timetable_version WHERE status = 'DRAFT';
SELECT COUNT(*) AS entries_after_deleting_the_draft FROM timetable_entry e
WHERE e.timetable_version_id NOT IN (SELECT id FROM timetable_version);
SELECT (SELECT COUNT(*) FROM timetable_entry) AS entries_left_in_total;
ROLLBACK;

SELECT COUNT(*) AS entries_after_the_rollback FROM timetable_entry;
