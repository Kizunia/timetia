-- 06_dcl.sql: who may do what on the Timetia lab database (SE2004 DBMS, practical 7: GRANT and REVOKE).
--
-- Run as an administrator (root) after 01, 02 and 05 (the views are granted below). It creates MySQL roles, gives them
-- privileges, creates users, and gives the users the roles. Everything happens on the lab server; the application's own
-- database account is created by its deployment, not here.
--
-- The three roles follow the three kinds of people who use the data:
--   timetia_reader    a student or a visitor: reads the published timetable and the state of tickets, through the views, and
--                     the codes and names of faculty; sees no table directly and none of the account ids.
--   timetia_scheduler the timetable office: reads everything, raises and changes tickets, applies changes to lectures.
--   timetia_admin     everything on timetia_lab, and may pass its privileges on.
--
-- The passwords below are for the lab only. Never reuse them.

USE timetia_lab;

-- Roles ---------------------------------------------------------------------------------------------------------------------

CREATE ROLE IF NOT EXISTS 'timetia_reader', 'timetia_scheduler', 'timetia_admin';

-- A reader sees data through views: a view hides the tables behind it, and with them every column it does not select.
GRANT SELECT ON timetia_lab.v_live_timetable  TO 'timetia_reader';
GRANT SELECT ON timetia_lab.v_ticket_overview TO 'timetia_reader';

-- A privilege can be given on single columns. The reader may know who a faculty member is but not which account they sign
-- in as (user_id), nor the audit columns.
GRANT SELECT (id, code, name, department_id) ON timetia_lab.faculty TO 'timetia_reader';
GRANT SELECT ON timetia_lab.department TO 'timetia_reader';

-- The office reads every table, and may write the ones a change touches, and nothing else.
GRANT SELECT ON timetia_lab.* TO 'timetia_scheduler';
GRANT INSERT, UPDATE ON timetia_lab.timetable_change_ticket TO 'timetia_scheduler';
GRANT INSERT, UPDATE ON timetia_lab.ticket_change_item      TO 'timetia_scheduler';
GRANT INSERT         ON timetia_lab.ticket_review           TO 'timetia_scheduler';
GRANT INSERT         ON timetia_lab.conflict_check_run      TO 'timetia_scheduler';
GRANT INSERT         ON timetia_lab.conflict_finding        TO 'timetia_scheduler';
GRANT UPDATE         ON timetia_lab.lecture_occurrence      TO 'timetia_scheduler';
GRANT INSERT, DELETE ON timetia_lab.occurrence_claim        TO 'timetia_scheduler';

GRANT ALL PRIVILEGES ON timetia_lab.* TO 'timetia_admin' WITH GRANT OPTION;

SHOW GRANTS FOR 'timetia_reader';
SHOW GRANTS FOR 'timetia_scheduler';

-- Users ---------------------------------------------------------------------------------------------------------------------

CREATE USER IF NOT EXISTS 'lab_reader'@'localhost'    IDENTIFIED BY 'lab-only-Reader#1';
CREATE USER IF NOT EXISTS 'lab_scheduler'@'localhost' IDENTIFIED BY 'lab-only-Scheduler#1';
CREATE USER IF NOT EXISTS 'lab_admin'@'localhost'     IDENTIFIED BY 'lab-only-Admin#1';

GRANT 'timetia_reader'    TO 'lab_reader'@'localhost';
GRANT 'timetia_scheduler' TO 'lab_scheduler'@'localhost';
GRANT 'timetia_admin'     TO 'lab_admin'@'localhost';

-- A role takes effect when it is active. Make each user's role active at sign-in.
SET DEFAULT ROLE 'timetia_reader'    TO 'lab_reader'@'localhost';
SET DEFAULT ROLE 'timetia_scheduler' TO 'lab_scheduler'@'localhost';
SET DEFAULT ROLE 'timetia_admin'     TO 'lab_admin'@'localhost';

SHOW GRANTS FOR 'lab_reader'@'localhost';
SHOW GRANTS FOR 'lab_reader'@'localhost' USING 'timetia_reader';

-- Trying it ------------------------------------------------------------------------------------------------------------------
-- Connect as each user and run the statements in the comments; the result is written beside each one.
--
--   mysql -u lab_reader -p timetia_lab                 (password: lab-only-Reader#1)
--     SELECT * FROM v_live_timetable LIMIT 3;                     -- works
--     SELECT id, code, name FROM faculty;                          -- works: those columns were granted
--     SELECT user_id FROM faculty;                                 -- ERROR 1143: no privilege on that column
--     SELECT * FROM lecture_occurrence;                            -- ERROR 1142: no privilege on the table
--     DELETE FROM department;                                      -- ERROR 1142
--
--   mysql -u lab_scheduler -p timetia_lab              (password: lab-only-Scheduler#1)
--     SELECT COUNT(*) FROM occurrence_claim;                       -- works
--     UPDATE lecture_occurrence SET is_cancelled = TRUE WHERE FALSE;   -- works (no row matches)
--     DELETE FROM teaching_assignment;                             -- ERROR 1142: the office cannot delete assignments

-- Taking a privilege back ------------------------------------------------------------------------------------------------------
-- REVOKE removes what GRANT gave, from the role, and every user holding the role loses it at once. The same statements in
-- the other direction:

REVOKE INSERT, DELETE ON timetia_lab.occurrence_claim FROM 'timetia_scheduler';
GRANT  INSERT, DELETE ON timetia_lab.occurrence_claim TO   'timetia_scheduler';

-- A user can also lose a role, and a whole user or role can be dropped. The lab ends by cleaning up (left commented out, so the
-- users above stay usable for the trial):
-- REVOKE 'timetia_reader' FROM 'lab_reader'@'localhost';
-- DROP USER IF EXISTS 'lab_reader'@'localhost', 'lab_scheduler'@'localhost', 'lab_admin'@'localhost';
-- DROP ROLE IF EXISTS 'timetia_reader', 'timetia_scheduler', 'timetia_admin';
