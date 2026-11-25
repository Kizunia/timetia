-- 07_tcl.sql: transactions on the Timetia lab database (SE2004 DBMS, practical 8: COMMIT, ROLLBACK, SAVEPOINT).
--
-- Run after 01_schema.sql and 02_seed.sql. This script CHANGES the data (it cancels a lecture and rewords a ticket); to run it
-- again from the start, drop timetia_lab and re-run 01 and 02.
--
-- A transaction is a group of statements that succeeds or fails as one. In Timetia, applying a ticket is such a group: the
-- lecture is changed, what it held is released and the ticket is closed, and a failure part-way must leave none of it behind.
-- The Java side does the same with Spring's @Transactional: the use case starts a transaction, the JDBC adapters run their
-- statements on its connection, and an exception rolls the lot back (see JdbcSupportIT).
--
-- Note that DDL (CREATE, ALTER, DROP) commits the open transaction by itself in MySQL, so this script has none inside one.

USE timetia_lab;

SELECT @@autocommit AS autocommit_is_on, @@transaction_isolation AS isolation_level;

SET @tenant   = UNHEX('00000000000070008000000000000001');
SET @roy_user = UNHEX('a1000000000070008000000000000002');

-- Part 1: COMMIT -----------------------------------------------------------------------------------------------------------
-- Dr Roy's DBMS lecture of SY-CSE-A on Monday 27 July at 10:00 is cancelled. First the ticket is raised (one transaction),
-- then it is applied (another). The lecture and its version are read first: the change is made only if the lecture still has
-- that version, which is how Timetia notices that someone else changed it in between (optimistic locking).

SELECT a.id, a.faculty_id, a.division_id, d.department_id
INTO @asg, @fac, @div, @dept
FROM teaching_assignment a
JOIN division d ON d.id = a.division_id
JOIN subject s  ON s.id = a.subject_id
WHERE d.code = 'SY-CSE-A' AND s.code = 'DBMS' AND a.session_type = 'THEORY';

SELECT id, version INTO @lecture, @lecture_version
FROM lecture_occurrence
WHERE assignment_id = @asg AND occurrence_date = '2026-07-27' AND effective_start_time = '10:00:00';

SET @ticket = UNHEX('f1000000000070008000000000000099');

START TRANSACTION;
INSERT INTO timetable_change_ticket (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    ticket_type, status, author_user_id, description, revision_no, assignment_id, department_id, division_id, batch_id,
    session_type, faculty_id, expires_at, is_conflicted)
VALUES (@ticket, @tenant, 0, '2026-07-20 08:00:00.000000', @roy_user, '2026-07-20 08:00:00.000000', @roy_user,
    'CANCEL', 'DRAFT', @roy_user, 'Department meeting', 1, @asg, @dept, @div, NULL,
    'THEORY', @fac, '2026-07-27 04:30:00.000000', FALSE);
INSERT INTO ticket_change_item (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    ticket_id, item_no, operation_type, source_occurrence_id, source_revision, reason, faculty_id)
VALUES (UNHEX('f2000000000070008000000000000099'), @tenant, 0, '2026-07-20 08:00:00.000000', @roy_user,
    '2026-07-20 08:00:00.000000', @roy_user, @ticket, 1, 'CANCEL', @lecture, @lecture_version, 'Department meeting', @fac);
COMMIT;

SELECT status, closed_at FROM timetable_change_ticket WHERE id = @ticket;

-- Applying: three statements, all or nothing.
START TRANSACTION;

UPDATE lecture_occurrence
SET is_cancelled = TRUE, changed_by_ticket_id = @ticket, version = version + 1,
    updated_at = '2026-07-20 08:05:00.000000', updated_by = @roy_user
WHERE id = @lecture AND version = @lecture_version;
SELECT ROW_COUNT() AS lecture_changed;        -- 1; it would be 0 if someone had changed the lecture since it was read

DELETE FROM occurrence_claim WHERE occurrence_id = @lecture;
SELECT ROW_COUNT() AS claims_released;         -- a cancelled lecture holds nothing: its teacher, room and class are free again

UPDATE timetable_change_ticket
SET status = 'APPLIED', approval_path = 'SELF', closed_at = '2026-07-20 08:05:00.000000', version = version + 1,
    updated_at = '2026-07-20 08:05:00.000000', updated_by = @roy_user
WHERE id = @ticket AND version = 0;
SELECT ROW_COUNT() AS ticket_closed;

COMMIT;

SELECT t.status, t.approval_path, o.is_cancelled, (SELECT COUNT(*) FROM occurrence_claim c WHERE c.occurrence_id = o.id) AS claims_left
FROM timetable_change_ticket t JOIN lecture_occurrence o ON o.id = @lecture
WHERE t.id = @ticket;

-- Part 2: ROLLBACK ---------------------------------------------------------------------------------------------------------
-- A mistake: every room claim is deleted. Inside the transaction the table shows the damage; ROLLBACK takes it back.

SELECT COUNT(*) INTO @claims_before FROM occurrence_claim;

START TRANSACTION;
DELETE FROM occurrence_claim WHERE claim_kind = 'ROOM';
SELECT @claims_before AS before_the_delete, COUNT(*) AS inside_the_transaction FROM occurrence_claim;
ROLLBACK;

SELECT @claims_before AS before_the_delete, COUNT(*) AS after_the_rollback FROM occurrence_claim;

-- Part 3: SAVEPOINT ----------------------------------------------------------------------------------------------------------
-- Prof Mehta's draft moves her DSA lecture of Wednesday 15 July to Monday 20 July at 10:00 in R101. Two steps in one transaction:
--   step A rewords the ticket, which is fine;
--   step B releases the lecture's room and takes R101 on the new date, which fails because R101 is already held then.
-- A savepoint marks the point between the two, so step B alone can be undone and step A kept.

SELECT t.id INTO @draft FROM timetable_change_ticket t WHERE t.status = 'DRAFT' AND t.description = 'Move DSA to the afternoon';
SELECT o.id INTO @dsa FROM lecture_occurrence o
JOIN teaching_assignment a ON a.id = o.assignment_id JOIN subject s ON s.id = a.subject_id
WHERE s.code = 'DSA' AND o.occurrence_date = '2026-07-15';
SELECT id INTO @room_101 FROM room WHERE code = 'R101';

START TRANSACTION;

UPDATE timetable_change_ticket SET description = 'Move DSA to Monday 10:00', version = version + 1 WHERE id = @draft;

SAVEPOINT after_rewording;

DELETE FROM occurrence_claim WHERE occurrence_id = @dsa AND claim_kind = 'ROOM';
SELECT COUNT(*) AS room_claims_of_the_lecture_now FROM occurrence_claim WHERE occurrence_id = @dsa AND claim_kind = 'ROOM';

-- INSERT IGNORE turns the duplicate-key error into a warning, so the script goes on and shows what happened. A plain INSERT
-- would stop with ERROR 1062 (Duplicate entry ... for key 'uq_occurrence_claim_cell'), which is what the application sees and
-- reports as a conflict (HTTP 409).
INSERT IGNORE INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                                     occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
VALUES (UUID_TO_BIN(UUID()), @tenant, 0, '2026-07-20 09:00:00.000000', @roy_user, '2026-07-20 09:00:00.000000', @roy_user,
        @dsa, 'ROOM', @room_101, '2026-07-20', 10);
SELECT ROW_COUNT() AS claims_inserted;         -- 0: the unique key refused it, R101 is held at 10:00 on 20 July
SHOW WARNINGS;

ROLLBACK TO SAVEPOINT after_rewording;         -- step B is undone ...
SELECT COUNT(*) AS room_claims_restored FROM occurrence_claim WHERE occurrence_id = @dsa AND claim_kind = 'ROOM';

COMMIT;                                        -- ... and step A is kept

SELECT description, version FROM timetable_change_ticket WHERE id = @draft;

-- Part 4: locking ------------------------------------------------------------------------------------------------------------
-- Two changes that want the same lectures must queue, not race. A locking read takes a lock on the rows it reads and holds it
-- until COMMIT or ROLLBACK (JdbcOccurrenceStore.lockAll does exactly this, in id order so that two changes cannot lock the
-- same lectures in opposite orders and wait for each other for ever).
--
-- To see it, use two sessions on the same database:
--   session 1:  START TRANSACTION;
--               SELECT id, version FROM lecture_occurrence WHERE occurrence_date = '2026-07-06' ORDER BY id FOR UPDATE;
--   session 2:  START TRANSACTION;
--               SELECT id, version FROM lecture_occurrence WHERE occurrence_date = '2026-07-06' ORDER BY id FOR UPDATE;
--               -- waits ...
--   session 1:  COMMIT;
--   session 2:  -- the rows come back; a lock that is never released ends with
--               -- ERROR 1205 (HY000): Lock wait timeout exceeded; try restarting transaction
--
-- Isolation: what one transaction sees of another's work. MySQL's default is REPEATABLE READ: a transaction reads the data as it
-- was when its first read began, however much others commit meanwhile.
SELECT @@transaction_isolation AS isolation_level;
-- SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;   -- each statement then sees what has been committed by then

-- Part 5: the same in Java -----------------------------------------------------------------------------------------------------
--   START TRANSACTION            connection.setAutoCommit(false), or a @Transactional method of a use case
--   COMMIT                       connection.commit(), or the @Transactional method returning normally
--   ROLLBACK                     connection.rollback(), or the @Transactional method throwing
--   SAVEPOINT x / ROLLBACK TO x  Savepoint x = connection.setSavepoint("x"); connection.rollback(x);
-- The JDBC adapters never open a transaction themselves: JdbcSupport takes the connection of the one the use case opened
-- (DataSourceUtils.getConnection), so everything a use case does commits or rolls back together.
