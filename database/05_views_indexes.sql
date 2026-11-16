-- 05_views_indexes.sql: views and indexes on the Timetia lab database (SE2004 DBMS, practical 7).
--
-- Run after 01_schema.sql and 02_seed.sql. This changes only the lab database timetia_lab. The application's own database is
-- owned by Flyway and is not touched; an index that proved useful in the application would be added there by a new migration.
--
-- The lab database holds one tenant, so the views do not filter on tenant_id; the application's queries always do.

USE timetia_lab;

-- Views ---------------------------------------------------------------------------------------------------------------------

-- The live timetable: each lecture as it is now (the effective columns), with the names a person reads. A view joins six
-- tables, so a reader needs one name instead of the join. Lectures a later version replaced (SUPERSEDED) are left out. A theory
-- lecture has no batch and an online one has no room, so those two joins are LEFT joins.
CREATE OR REPLACE VIEW v_live_timetable AS
SELECT o.id              AS lecture_id,
       o.effective_date  AS lecture_date,
       o.effective_start_time AS starts,
       o.effective_end_time   AS ends,
       dv.code           AS division,
       b.code            AS batch,
       s.code            AS subject_code,
       s.name            AS subject_name,
       o.session_type    AS session_type,
       f.name            AS faculty,
       r.code            AS room,
       o.effective_mode  AS mode,
       CASE
           WHEN o.is_cancelled                          THEN 'CANCELLED'
           WHEN o.effective_faculty_id <> o.faculty_id  THEN 'SUBSTITUTED'
           WHEN o.changed_by_ticket_id IS NOT NULL      THEN 'CHANGED'
           ELSE 'AS PUBLISHED'
       END               AS status
FROM lecture_occurrence o
JOIN division dv            ON dv.id = o.division_id
JOIN teaching_assignment a  ON a.id = o.assignment_id
JOIN subject s              ON s.id = a.subject_id
JOIN faculty f              ON f.id = o.effective_faculty_id
LEFT JOIN batch b           ON b.id = o.batch_id
LEFT JOIN room r            ON r.id = o.effective_room_id
WHERE o.record_status = 'ACTIVE';

-- Tickets with the teacher, the division, what the first change does, and two counts worked out for each ticket by
-- correlated subqueries: the reviews still waiting in the ticket's current revision, and the conflicts found for it.
CREATE OR REPLACE VIEW v_ticket_overview AS
SELECT t.id             AS ticket_id,
       t.ticket_type    AS ticket_type,
       t.status         AS status,
       t.created_at     AS raised_at,
       f.name           AS teacher,
       dv.code          AS division,
       c.operation_type AS operation,
       c.reason         AS reason,
       (SELECT COUNT(*) FROM ticket_review r
         WHERE r.ticket_id = t.id AND r.ticket_revision_no = t.revision_no AND r.review_status = 'PENDING') AS pending_reviews,
       (SELECT COUNT(*) FROM conflict_finding cf WHERE cf.ticket_id = t.id) AS conflicts_found
FROM timetable_change_ticket t
JOIN faculty f           ON f.id = t.faculty_id
JOIN division dv         ON dv.id = t.division_id
JOIN ticket_change_item c ON c.ticket_id = t.id AND c.item_no = 1;

-- How much each faculty member teaches in a week, from the published timetables: assignments, and hours. Every faculty member is
-- listed, also one with no assignment, so the joins to assignments and entries are LEFT joins; the entry is joined to its
-- version first, inside the parentheses, so that only entries of a published version count.
CREATE OR REPLACE VIEW v_teaching_load AS
SELECT f.code                                    AS faculty_code,
       f.name                                    AS faculty,
       d.code                                    AS department,
       COUNT(DISTINCT e.assignment_id)           AS assignments_in_timetable,
       COALESCE(SUM(HOUR(e.end_time) - HOUR(e.start_time)), 0) AS hours_per_week
FROM faculty f
JOIN department d ON d.id = f.department_id
LEFT JOIN teaching_assignment a ON a.faculty_id = f.id AND a.deleted_at IS NULL
LEFT JOIN (timetable_entry e JOIN timetable_version v ON v.id = e.timetable_version_id AND v.status = 'PUBLISHED')
       ON e.assignment_id = a.id
WHERE f.deleted_at IS NULL
GROUP BY f.id, f.code, f.name, d.code;

SELECT * FROM v_live_timetable WHERE lecture_date = '2026-07-06' ORDER BY starts, division;
SELECT * FROM v_ticket_overview ORDER BY raised_at;
SELECT * FROM v_teaching_load ORDER BY hours_per_week DESC;

SHOW FULL TABLES IN timetia_lab WHERE Table_type = 'VIEW';

-- Indexes -------------------------------------------------------------------------------------------------------------------
-- The schema already indexes the columns the application filters by most (see the INDEX lines in 01_schema.sql). These three
-- serve questions the JDBC adapters ask that no existing index answers well. Every application query names the tenant first,
-- so tenant_id leads each index.

-- 1. The expiry job asks for the tickets not yet applied whose lecture has started, oldest first (JdbcTicketStore.openExpiredBy):
--    a filter on status, a range on expires_at, sorted by expires_at.
CREATE INDEX ticket_by_expiry_idx ON timetable_change_ticket (tenant_id, status, expires_at);

-- 2. Finding the tickets under review that want a slot on a date (JdbcTicketStore.inReviewAffectedBy) looks the changes up by
--    their target date.
CREATE INDEX ticket_change_by_target_date_idx ON ticket_change_item (tenant_id, target_date);

-- 3. Which assignments teach a subject: the join from subject to teaching_assignment.
CREATE INDEX assignment_by_subject_idx ON teaching_assignment (tenant_id, subject_id);

-- The columns of each index, in order.
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = 'timetia_lab'
  AND index_name IN ('ticket_by_expiry_idx', 'ticket_change_by_target_date_idx', 'assignment_by_subject_idx')
GROUP BY table_name, index_name;

-- Does an index help? On the seed data nothing shows: with five tickets MySQL reads the whole table, which is cheaper than
-- using any index. So here is the same shape of question on a scratch table of 50,000 tickets. Read two columns of EXPLAIN:
-- "key" (which index is used) and "rows" (how many rows MySQL expects to read).
CREATE TABLE demo_ticket_volume (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    tenant_id  BINARY(16)  NOT NULL,
    status     VARCHAR(12) NOT NULL,
    expires_at DATETIME(6) NOT NULL
);

SET SESSION cte_max_recursion_depth = 60000;
INSERT INTO demo_ticket_volume (tenant_id, status, expires_at)
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 50000)
SELECT UNHEX('00000000000070008000000000000001'),
       ELT(1 + i % 3, 'DRAFT', 'IN_REVIEW', 'APPLIED'),
       TIMESTAMP('2026-07-01 00:00:00') + INTERVAL i MINUTE
FROM n;

SELECT COUNT(*) AS tickets_in_the_scratch_table FROM demo_ticket_volume;

-- Without an index: every row is read ("type" is ALL, "key" is NULL).
EXPLAIN SELECT id, expires_at FROM demo_ticket_volume
WHERE tenant_id = UNHEX('00000000000070008000000000000001') AND status = 'IN_REVIEW' AND expires_at <= '2026-07-01 12:00:00'
ORDER BY expires_at;

-- The same index as ticket_by_expiry_idx: only the rows in the range are read, already in order (no "Using filesort").
CREATE INDEX demo_by_expiry_idx ON demo_ticket_volume (tenant_id, status, expires_at);

EXPLAIN SELECT id, expires_at FROM demo_ticket_volume
WHERE tenant_id = UNHEX('00000000000070008000000000000001') AND status = 'IN_REVIEW' AND expires_at <= '2026-07-01 12:00:00'
ORDER BY expires_at;

-- An index is not free: every INSERT and UPDATE on the table keeps it up to date, and it takes space. Take the scratch table away.
DROP TABLE demo_ticket_volume;
-- Dropping a real index is as simple as making it:
-- DROP INDEX assignment_by_subject_idx ON teaching_assignment;
