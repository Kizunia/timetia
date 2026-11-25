-- 02_seed.sql: sample data for the Timetia lab database (SE2004 DBMS, practical 4: DML).
--
-- Run after 01_schema.sql. It is a small campus, built so that joins, outer joins, subqueries and aggregates have something
-- to find:
--   * three departments (CSE, ME, ENTC); ENTC has a division but no timetable, and no batches;
--   * a subject nobody teaches (MATHS, SIGNALS), a faculty member with no assignments (Joshi), a room that is never used (R301),
--     and a term with no timetable (SEM4);
--   * three published timetables (CSE-A, CSE-B, ME-A) and a draft for CSE-A, with four weeks of dated lectures from Monday
--     6 July 2026, and the claims those lectures hold (a faculty member, a room, a class or batch, for every hour);
--   * five tickets: one applied (a cancellation), one under review, one draft with a conflict, one withdrawn, one rejected,
--     with their changes, reviews, conflict checks and the notifications they caused.
--
-- Ids are BINARY(16). The reference rows get readable fixed ids through the variables below, so the later statements can name
-- them; the generated lectures and claims get UUID_TO_BIN(UUID()). Times are UTC, as the application stores them (the campus
-- clock is five and a half hours ahead).

USE timetia_lab;

SET @tenant = UNHEX('00000000000070008000000000000001');
SET @system = UNHEX('00000000000000000000000000000000');
SET @now    = '2026-07-01 00:00:00.000000';

-- People are known only by account id here (the sign-in tables are not in the lab database).
SET @u_rao    = UNHEX('a1000000000070008000000000000001');
SET @u_roy    = UNHEX('a1000000000070008000000000000002');
SET @u_mehta  = UNHEX('a1000000000070008000000000000003');
SET @u_iyer   = UNHEX('a1000000000070008000000000000004');
SET @u_hod_cse = UNHEX('a2000000000070008000000000000001');
SET @u_hod_me  = UNHEX('a2000000000070008000000000000002');
SET @u_cr_cse_a = UNHEX('a3000000000070008000000000000001');

-- Departments ------------------------------------------------------------------------------------------------------------

SET @dept_cse  = UNHEX('d1000000000070008000000000000001');
SET @dept_me   = UNHEX('d1000000000070008000000000000002');
SET @dept_entc = UNHEX('d1000000000070008000000000000003');

INSERT INTO department (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name) VALUES
  (@dept_cse,  @tenant, 0, @now, @system, @now, @system, 'CSE',  'Computer Engineering'),
  (@dept_me,   @tenant, 0, @now, @system, @now, @system, 'ME',   'Mechanical Engineering'),
  (@dept_entc, @tenant, 0, @now, @system, @now, @system, 'ENTC', 'Electronics and Telecommunication');

-- Divisions and batches --------------------------------------------------------------------------------------------------

SET @div_cse_a  = UNHEX('d2000000000070008000000000000001');
SET @div_cse_b  = UNHEX('d2000000000070008000000000000002');
SET @div_me_a   = UNHEX('d2000000000070008000000000000003');
SET @div_entc_a = UNHEX('d2000000000070008000000000000004');

INSERT INTO division (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, department_id) VALUES
  (@div_cse_a,  @tenant, 0, @now, @system, @now, @system, 'SY-CSE-A',  'SY Computer Engineering A', @dept_cse),
  (@div_cse_b,  @tenant, 0, @now, @system, @now, @system, 'SY-CSE-B',  'SY Computer Engineering B', @dept_cse),
  (@div_me_a,   @tenant, 0, @now, @system, @now, @system, 'SY-ME-A',   'SY Mechanical A',           @dept_me),
  (@div_entc_a, @tenant, 0, @now, @system, @now, @system, 'SY-ENTC-A', 'SY Electronics A',          @dept_entc);

SET @batch_a1 = UNHEX('d3000000000070008000000000000001');
SET @batch_a2 = UNHEX('d3000000000070008000000000000002');
SET @batch_b1 = UNHEX('d3000000000070008000000000000003');
SET @batch_b2 = UNHEX('d3000000000070008000000000000004');
SET @batch_m1 = UNHEX('d3000000000070008000000000000005');

INSERT INTO batch (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, division_id) VALUES
  (@batch_a1, @tenant, 0, @now, @system, @now, @system, 'CSE-A1', 'CSE A batch 1', @div_cse_a),
  (@batch_a2, @tenant, 0, @now, @system, @now, @system, 'CSE-A2', 'CSE A batch 2', @div_cse_a),
  (@batch_b1, @tenant, 0, @now, @system, @now, @system, 'CSE-B1', 'CSE B batch 1', @div_cse_b),
  (@batch_b2, @tenant, 0, @now, @system, @now, @system, 'CSE-B2', 'CSE B batch 2', @div_cse_b),
  (@batch_m1, @tenant, 0, @now, @system, @now, @system, 'ME-A1',  'ME A batch 1',  @div_me_a);

-- Subjects, rooms, faculty, terms -----------------------------------------------------------------------------------------

SET @sub_oop     = UNHEX('d4000000000070008000000000000001');
SET @sub_dbms    = UNHEX('d4000000000070008000000000000002');
SET @sub_dsa     = UNHEX('d4000000000070008000000000000003');
SET @sub_maths   = UNHEX('d4000000000070008000000000000004');
SET @sub_thermo  = UNHEX('d4000000000070008000000000000005');
SET @sub_fluid   = UNHEX('d4000000000070008000000000000006');
SET @sub_signals = UNHEX('d4000000000070008000000000000007');

INSERT INTO subject (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name) VALUES
  (@sub_oop,     @tenant, 0, @now, @system, @now, @system, 'OOP',     'Object Oriented Programming'),
  (@sub_dbms,    @tenant, 0, @now, @system, @now, @system, 'DBMS',    'Database Management Systems'),
  (@sub_dsa,     @tenant, 0, @now, @system, @now, @system, 'DSA',     'Data Structures and Algorithms'),
  (@sub_maths,   @tenant, 0, @now, @system, @now, @system, 'MATHS',   'Engineering Mathematics III'),
  (@sub_thermo,  @tenant, 0, @now, @system, @now, @system, 'THERMO',  'Thermodynamics'),
  (@sub_fluid,   @tenant, 0, @now, @system, @now, @system, 'FLUID',   'Fluid Mechanics'),
  (@sub_signals, @tenant, 0, @now, @system, @now, @system, 'SIGNALS', 'Signals and Systems');

SET @room_101  = UNHEX('d5000000000070008000000000000001');
SET @room_102  = UNHEX('d5000000000070008000000000000002');
SET @room_201  = UNHEX('d5000000000070008000000000000003');
SET @room_lab1 = UNHEX('d5000000000070008000000000000004');
SET @room_lab2 = UNHEX('d5000000000070008000000000000005');
SET @room_tut1 = UNHEX('d5000000000070008000000000000006');
SET @room_301  = UNHEX('d5000000000070008000000000000007');

INSERT INTO room (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, room_type, capacity) VALUES
  (@room_101,  @tenant, 0, @now, @system, @now, @system, 'R101',  'Classroom 101',      'CLASSROOM',     70),
  (@room_102,  @tenant, 0, @now, @system, @now, @system, 'R102',  'Classroom 102',      'CLASSROOM',     70),
  (@room_201,  @tenant, 0, @now, @system, @now, @system, 'R201',  'Classroom 201',      'CLASSROOM',     60),
  (@room_lab1, @tenant, 0, @now, @system, @now, @system, 'LAB1',  'Computer Lab 1',     'LAB',           30),
  (@room_lab2, @tenant, 0, @now, @system, @now, @system, 'LAB2',  'Computer Lab 2',     'LAB',           30),
  (@room_tut1, @tenant, 0, @now, @system, @now, @system, 'TUT1',  'Tutorial Room 1',    'TUTORIAL_ROOM', 25),
  (@room_301,  @tenant, 0, @now, @system, @now, @system, 'R301',  'Classroom 301',      'CLASSROOM',     NULL);

SET @fac_rao     = UNHEX('d6000000000070008000000000000001');
SET @fac_roy     = UNHEX('d6000000000070008000000000000002');
SET @fac_mehta   = UNHEX('d6000000000070008000000000000003');
SET @fac_iyer    = UNHEX('d6000000000070008000000000000004');
SET @fac_kulkarni = UNHEX('d6000000000070008000000000000005');
SET @fac_joshi   = UNHEX('d6000000000070008000000000000006');

INSERT INTO faculty (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, department_id, user_id) VALUES
  (@fac_rao,      @tenant, 0, @now, @system, @now, @system, 'RAO',      'Dr Rao',      @dept_cse,  @u_rao),
  (@fac_roy,      @tenant, 0, @now, @system, @now, @system, 'ROY',      'Dr Roy',      @dept_cse,  @u_roy),
  (@fac_mehta,    @tenant, 0, @now, @system, @now, @system, 'MEHTA',    'Prof Mehta',  @dept_cse,  @u_mehta),
  (@fac_iyer,     @tenant, 0, @now, @system, @now, @system, 'IYER',     'Dr Iyer',     @dept_me,   @u_iyer),
  (@fac_kulkarni, @tenant, 0, @now, @system, @now, @system, 'KULKARNI', 'Prof Kulkarni', @dept_me,  NULL),
  (@fac_joshi,    @tenant, 0, @now, @system, @now, @system, 'JOSHI',    'Dr Joshi',    @dept_entc, NULL);

SET @term_sem3 = UNHEX('d7000000000070008000000000000001');
SET @term_sem4 = UNHEX('d7000000000070008000000000000002');

INSERT INTO academic_term (id, tenant_id, version, created_at, created_by, updated_at, updated_by, code, name, start_date, end_date) VALUES
  (@term_sem3, @tenant, 0, @now, @system, @now, @system, 'SEM3-2026', 'Semester III, 2026-27', '2026-07-01', '2026-11-30'),
  (@term_sem4, @tenant, 0, @now, @system, @now, @system, 'SEM4-2027', 'Semester IV, 2026-27',  '2027-01-05', '2027-05-15');

-- Teaching assignments: who teaches what to whom. Theory has no batch; a lab or tutorial names its batch. ------------------

SET @asg_oop_a    = UNHEX('d8000000000070008000000000000001');
SET @asg_dbms_a   = UNHEX('d8000000000070008000000000000002');
SET @asg_dbmslab_a1 = UNHEX('d8000000000070008000000000000003');
SET @asg_dbmslab_a2 = UNHEX('d8000000000070008000000000000004');
SET @asg_dsa_a    = UNHEX('d8000000000070008000000000000005');
SET @asg_oop_b    = UNHEX('d8000000000070008000000000000006');
SET @asg_dbms_b   = UNHEX('d8000000000070008000000000000007');
SET @asg_thermo_m = UNHEX('d8000000000070008000000000000008');
SET @asg_fluid_m  = UNHEX('d8000000000070008000000000000009');
SET @asg_thermotut_m1 = UNHEX('d800000000007000800000000000000a');

INSERT INTO teaching_assignment (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                                 division_id, batch_id, subject_id, faculty_id, session_type) VALUES
  (@asg_oop_a,         @tenant, 0, @now, @system, @now, @system, @div_cse_a, NULL,      @sub_oop,    @fac_rao,      'THEORY'),
  (@asg_dbms_a,        @tenant, 0, @now, @system, @now, @system, @div_cse_a, NULL,      @sub_dbms,   @fac_roy,      'THEORY'),
  (@asg_dbmslab_a1,    @tenant, 0, @now, @system, @now, @system, @div_cse_a, @batch_a1, @sub_dbms,   @fac_roy,      'LAB'),
  (@asg_dbmslab_a2,    @tenant, 0, @now, @system, @now, @system, @div_cse_a, @batch_a2, @sub_dbms,   @fac_mehta,    'LAB'),
  (@asg_dsa_a,         @tenant, 0, @now, @system, @now, @system, @div_cse_a, NULL,      @sub_dsa,    @fac_mehta,    'THEORY'),
  (@asg_oop_b,         @tenant, 0, @now, @system, @now, @system, @div_cse_b, NULL,      @sub_oop,    @fac_rao,      'THEORY'),
  (@asg_dbms_b,        @tenant, 0, @now, @system, @now, @system, @div_cse_b, NULL,      @sub_dbms,   @fac_roy,      'THEORY'),
  (@asg_thermo_m,      @tenant, 0, @now, @system, @now, @system, @div_me_a,  NULL,      @sub_thermo, @fac_iyer,     'THEORY'),
  (@asg_fluid_m,       @tenant, 0, @now, @system, @now, @system, @div_me_a,  NULL,      @sub_fluid,  @fac_kulkarni, 'THEORY'),
  (@asg_thermotut_m1,  @tenant, 0, @now, @system, @now, @system, @div_me_a,  @batch_m1, @sub_thermo, @fac_iyer,     'TUTORIAL');

-- Timetable versions and their weekly entries -----------------------------------------------------------------------------

SET @ver_cse_a = UNHEX('e1000000000070008000000000000001');
SET @ver_cse_b = UNHEX('e1000000000070008000000000000002');
SET @ver_me_a  = UNHEX('e1000000000070008000000000000003');
SET @ver_cse_a_draft = UNHEX('e1000000000070008000000000000004');

INSERT INTO timetable_version (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                               division_id, term_id, effective_from, status) VALUES
  (@ver_cse_a,       @tenant, 1, @now, @system, @now, @system, @div_cse_a, @term_sem3, '2026-07-06', 'PUBLISHED'),
  (@ver_cse_b,       @tenant, 1, @now, @system, @now, @system, @div_cse_b, @term_sem3, '2026-07-06', 'PUBLISHED'),
  (@ver_me_a,        @tenant, 1, @now, @system, @now, @system, @div_me_a,  @term_sem3, '2026-07-06', 'PUBLISHED'),
  (@ver_cse_a_draft, @tenant, 0, @now, @system, @now, @system, @div_cse_a, @term_sem3, '2026-08-03', 'DRAFT');

-- day_of_week: 1 is Monday. Online lectures have no room.
INSERT INTO timetable_entry (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                             timetable_version_id, assignment_id, day_of_week, start_time, end_time, room_id, mode, meeting_link) VALUES
  (UNHEX('e2000000000070008000000000000001'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_oop_a,       1, '09:00', '10:00', @room_101,  'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000002'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_dbms_a,      1, '10:00', '11:00', @room_101,  'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000003'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_dbmslab_a1,  2, '09:00', '11:00', @room_lab1, 'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000004'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_dbmslab_a2,  2, '09:00', '11:00', @room_lab2, 'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000005'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_dsa_a,       3, '11:00', '12:00', @room_102,  'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000006'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a, @asg_dbms_a,      3, '14:00', '15:00', NULL,       'ONLINE',    'https://meet.example/dbms-a'),
  (UNHEX('e2000000000070008000000000000007'), @tenant, 0, @now, @system, @now, @system, @ver_cse_b, @asg_oop_b,       1, '11:00', '12:00', @room_101,  'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000008'), @tenant, 0, @now, @system, @now, @system, @ver_cse_b, @asg_dbms_b,      2, '11:00', '12:00', @room_101,  'IN_PERSON', NULL),
  (UNHEX('e2000000000070008000000000000009'), @tenant, 0, @now, @system, @now, @system, @ver_me_a,  @asg_thermo_m,    1, '09:00', '10:00', @room_201,  'IN_PERSON', NULL),
  (UNHEX('e200000000007000800000000000000a'), @tenant, 0, @now, @system, @now, @system, @ver_me_a,  @asg_fluid_m,     4, '10:00', '11:00', @room_201,  'IN_PERSON', NULL),
  (UNHEX('e200000000007000800000000000000b'), @tenant, 0, @now, @system, @now, @system, @ver_me_a,  @asg_thermotut_m1, 5, '14:00', '15:00', @room_tut1, 'IN_PERSON', NULL),
  (UNHEX('e200000000007000800000000000000c'), @tenant, 0, @now, @system, @now, @system, @ver_cse_a_draft, @asg_oop_a, 4, '09:00', '10:00', @room_101,  'IN_PERSON', NULL);

-- Publishing: the dated lectures --------------------------------------------------------------------------------------------
-- What publishing a version does: one lecture for every entry on every date of the first four weeks. The derived table w
-- counts the weeks 0 to 3; the date is the Monday the version takes effect on, plus the weeks, plus the entry's weekday.
-- At first a lecture is exactly as the base timetable has it: the effective columns equal the base ones.

INSERT INTO lecture_occurrence (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    timetable_entry_id, timetable_version_id, assignment_id, department_id, division_id, batch_id, session_type,
    faculty_id, effective_faculty_id, occurrence_date, start_time, end_time, room_id, mode, meeting_link,
    effective_date, effective_start_time, effective_end_time, effective_room_id, effective_mode, effective_meeting_link,
    is_cancelled, changed_by_ticket_id, record_status)
SELECT UUID_TO_BIN(UUID()), @tenant, 0, @now, @system, @now, @system,
       e.id, v.id, a.id, d.department_id, a.division_id, a.batch_id, a.session_type,
       a.faculty_id, a.faculty_id,
       DATE_ADD(v.effective_from, INTERVAL (w.n * 7 + e.day_of_week - 1) DAY), e.start_time, e.end_time, e.room_id, e.mode, e.meeting_link,
       DATE_ADD(v.effective_from, INTERVAL (w.n * 7 + e.day_of_week - 1) DAY), e.start_time, e.end_time, e.room_id, e.mode, e.meeting_link,
       FALSE, NULL, 'ACTIVE'
FROM timetable_entry e
JOIN timetable_version v    ON v.id = e.timetable_version_id AND v.status = 'PUBLISHED'
JOIN teaching_assignment a  ON a.id = e.assignment_id
JOIN division d             ON d.id = a.division_id
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) w;

-- Claims: every lecture holds its faculty member, its room and its students for every hour it lasts ------------------------
-- The derived table h counts the hours of a lecture (0 for the first hour, 1 for the second of a two hour lab). The unique key
-- of occurrence_claim would refuse any of these inserts if two lectures wanted one thing at the same hour.

INSERT INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                              occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
SELECT UUID_TO_BIN(UUID()), @tenant, 0, @now, @system, @now, @system, o.id, 'FACULTY', o.effective_faculty_id,
       o.effective_date, HOUR(o.effective_start_time) + h.n
FROM lecture_occurrence o
JOIN (SELECT 0 AS n UNION ALL SELECT 1) h ON h.n < HOUR(o.effective_end_time) - HOUR(o.effective_start_time)
WHERE o.is_cancelled = FALSE;

INSERT INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                              occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
SELECT UUID_TO_BIN(UUID()), @tenant, 0, @now, @system, @now, @system, o.id, 'ROOM', o.effective_room_id,
       o.effective_date, HOUR(o.effective_start_time) + h.n
FROM lecture_occurrence o
JOIN (SELECT 0 AS n UNION ALL SELECT 1) h ON h.n < HOUR(o.effective_end_time) - HOUR(o.effective_start_time)
WHERE o.is_cancelled = FALSE AND o.effective_room_id IS NOT NULL;

-- A theory lecture is for the whole division ...
INSERT INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                              occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
SELECT UUID_TO_BIN(UUID()), @tenant, 0, @now, @system, @now, @system, o.id, 'DIVISION', o.division_id,
       o.effective_date, HOUR(o.effective_start_time) + h.n
FROM lecture_occurrence o
JOIN (SELECT 0 AS n UNION ALL SELECT 1) h ON h.n < HOUR(o.effective_end_time) - HOUR(o.effective_start_time)
WHERE o.is_cancelled = FALSE AND o.session_type = 'THEORY';

-- ... and so for every batch in it; a lab or a tutorial is for its own batch only.
INSERT INTO occurrence_claim (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
                              occurrence_id, claim_kind, resource_id, claim_date, cell_hour)
SELECT UUID_TO_BIN(UUID()), @tenant, 0, @now, @system, @now, @system, o.id, 'STUDENT_BATCH', b.id,
       o.effective_date, HOUR(o.effective_start_time) + h.n
FROM lecture_occurrence o
JOIN (SELECT 0 AS n UNION ALL SELECT 1) h ON h.n < HOUR(o.effective_end_time) - HOUR(o.effective_start_time)
JOIN batch b ON (o.session_type = 'THEORY' AND b.division_id = o.division_id) OR (o.session_type <> 'THEORY' AND b.id = o.batch_id)
WHERE o.is_cancelled = FALSE;

-- Tickets -------------------------------------------------------------------------------------------------------------------
-- The lectures the tickets are about, found by assignment and date.

SELECT id INTO @occ_oop_a_0713   FROM lecture_occurrence WHERE assignment_id = @asg_oop_a    AND occurrence_date = '2026-07-13';
SELECT id INTO @occ_dbms_a_0720  FROM lecture_occurrence WHERE assignment_id = @asg_dbms_a   AND occurrence_date = '2026-07-20' AND effective_start_time = '10:00';
SELECT id INTO @occ_dsa_a_0715   FROM lecture_occurrence WHERE assignment_id = @asg_dsa_a    AND occurrence_date = '2026-07-15';
SELECT id INTO @occ_oop_b_0720   FROM lecture_occurrence WHERE assignment_id = @asg_oop_b    AND occurrence_date = '2026-07-20';
SELECT id INTO @occ_thermo_0713  FROM lecture_occurrence WHERE assignment_id = @asg_thermo_m AND occurrence_date = '2026-07-13';

SET @t_cancel   = UNHEX('f1000000000070008000000000000001');
SET @t_review   = UNHEX('f1000000000070008000000000000002');
SET @t_conflict = UNHEX('f1000000000070008000000000000003');
SET @t_withdrawn = UNHEX('f1000000000070008000000000000004');
SET @t_rejected = UNHEX('f1000000000070008000000000000005');

-- expires_at is when the earliest lecture the ticket involves starts (09:00 on the campus clock is 03:30 UTC).
INSERT INTO timetable_change_ticket (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    ticket_type, status, approval_path, author_user_id, acting_faculty_id, description, revision_no,
    assignment_id, department_id, division_id, batch_id, session_type, faculty_id,
    expires_at, is_conflicted, conflicted_since, closed_at, reverts_ticket_id, reverted_by_ticket_id) VALUES
  -- Dr Rao cancels one OOP lecture himself: applied at once, so it is closed.
  (@t_cancel, @tenant, 1, '2026-07-08 10:00:00.000000', @u_rao, '2026-07-08 10:00:05.000000', @u_rao,
   'CANCEL', 'APPLIED', 'SELF', @u_rao, NULL, 'Away at a conference', 1,
   @asg_oop_a, @dept_cse, @div_cse_a, NULL, 'THEORY', @fac_rao,
   '2026-07-13 03:30:00.000000', FALSE, NULL, '2026-07-08 10:00:05.000000', NULL, NULL),
  -- Dr Roy moves a DBMS lecture; the class representative and the HOD have been asked.
  (@t_review, @tenant, 1, '2026-07-10 09:00:00.000000', @u_roy, '2026-07-10 09:05:00.000000', @u_roy,
   'RESCHEDULE', 'IN_REVIEW', NULL, @u_roy, NULL, 'Clash with a seminar', 1,
   @asg_dbms_a, @dept_cse, @div_cse_a, NULL, 'THEORY', @fac_roy,
   '2026-07-20 04:30:00.000000', FALSE, NULL, NULL, NULL, NULL),
  -- Prof Mehta drafts a move of DSA into Monday 20 July 10:00, which R101 and SY-CSE-A already hold: flagged, not yet sent.
  (@t_conflict, @tenant, 0, '2026-07-11 11:00:00.000000', @u_mehta, '2026-07-11 11:00:00.000000', @u_mehta,
   'RESCHEDULE', 'DRAFT', NULL, @u_mehta, NULL, 'Move DSA to the afternoon', 1,
   @asg_dsa_a, @dept_cse, @div_cse_a, NULL, 'THEORY', @fac_mehta,
   '2026-07-15 05:30:00.000000', TRUE, '2026-07-11 11:00:00.000000', NULL, NULL, NULL),
  -- Dr Rao asked Dr Roy to take a CSE-B lecture, then withdrew the ticket.
  (@t_withdrawn, @tenant, 1, '2026-07-12 08:00:00.000000', @u_rao, '2026-07-12 09:00:00.000000', @u_rao,
   'SUBSTITUTE', 'WITHDRAWN', NULL, @u_rao, NULL, 'Needs cover', 1,
   @asg_oop_b, @dept_cse, @div_cse_b, NULL, 'THEORY', @fac_rao,
   '2026-07-20 05:30:00.000000', FALSE, NULL, '2026-07-12 09:00:00.000000', NULL, NULL),
  -- Dr Iyer's move was rejected by a reviewer, who gave a reason.
  (@t_rejected, @tenant, 1, '2026-07-09 12:00:00.000000', @u_iyer, '2026-07-09 15:00:00.000000', @u_iyer,
   'RESCHEDULE', 'REJECTED', NULL, @u_iyer, NULL, 'Move Thermodynamics', 1,
   @asg_thermo_m, @dept_me, @div_me_a, NULL, 'THEORY', @fac_iyer,
   '2026-07-13 03:30:00.000000', FALSE, NULL, NULL, NULL, NULL);

-- What each ticket does to a lecture. A move names its destination; a cancellation its reason; a substitute who takes over.
INSERT INTO ticket_change_item (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    ticket_id, item_no, operation_type, source_occurrence_id, source_revision, reason, faculty_id,
    target_date, target_start_time, target_end_time, target_mode, target_room_id, target_meeting_link, target_faculty_id,
    before_faculty_id, before_date, before_start_time, before_end_time, before_mode, before_room_id) VALUES
  (UNHEX('f2000000000070008000000000000001'), @tenant, 1, '2026-07-08 10:00:00.000000', @u_rao, '2026-07-08 10:00:05.000000', @u_rao,
   @t_cancel, 1, 'CANCEL', @occ_oop_a_0713, 0, 'Away at a conference', @fac_rao,
   NULL, NULL, NULL, NULL, NULL, NULL, NULL,
   @fac_rao, '2026-07-13', '09:00', '10:00', 'IN_PERSON', @room_101),
  (UNHEX('f2000000000070008000000000000002'), @tenant, 0, '2026-07-10 09:00:00.000000', @u_roy, '2026-07-10 09:00:00.000000', @u_roy,
   @t_review, 1, 'MOVE', @occ_dbms_a_0720, 0, NULL, @fac_roy,
   '2026-07-21', '12:00', '13:00', 'IN_PERSON', @room_102, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, NULL),
  (UNHEX('f2000000000070008000000000000003'), @tenant, 0, '2026-07-11 11:00:00.000000', @u_mehta, '2026-07-11 11:00:00.000000', @u_mehta,
   @t_conflict, 1, 'MOVE', @occ_dsa_a_0715, 0, NULL, @fac_mehta,
   '2026-07-20', '10:00', '11:00', 'IN_PERSON', @room_101, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, NULL),
  (UNHEX('f2000000000070008000000000000004'), @tenant, 0, '2026-07-12 08:00:00.000000', @u_rao, '2026-07-12 08:00:00.000000', @u_rao,
   @t_withdrawn, 1, 'REASSIGN_FACULTY', @occ_oop_b_0720, 0, NULL, @fac_rao,
   NULL, NULL, NULL, NULL, NULL, NULL, @fac_roy,
   NULL, NULL, NULL, NULL, NULL, NULL),
  (UNHEX('f2000000000070008000000000000005'), @tenant, 0, '2026-07-09 12:00:00.000000', @u_iyer, '2026-07-09 12:00:00.000000', @u_iyer,
   @t_rejected, 1, 'MOVE', @occ_thermo_0713, 0, NULL, @fac_iyer,
   '2026-07-14', '09:00', '10:00', 'IN_PERSON', @room_201, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, NULL);

-- The applied cancellation is laid over its lecture, and a cancelled lecture holds nothing.
UPDATE lecture_occurrence
SET is_cancelled = TRUE, changed_by_ticket_id = @t_cancel, version = version + 1, updated_at = '2026-07-08 10:00:05.000000', updated_by = @u_rao
WHERE id = @occ_oop_a_0713;

DELETE FROM occurrence_claim WHERE occurrence_id = @occ_oop_a_0713;

-- Reviews: who was asked about which revision of a ticket, and what they answered.
INSERT INTO ticket_review (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    ticket_id, ticket_revision_no, reviewer_user_id, reviewer_capacity, review_status, decision_reason, decided_at,
    is_escalation, escalated_from_review_id) VALUES
  (UNHEX('f3000000000070008000000000000001'), @tenant, 1, '2026-07-10 09:05:00.000000', @u_roy, '2026-07-10 14:00:00.000000', @u_cr_cse_a,
   @t_review, 1, @u_cr_cse_a, 'CR', 'APPROVED', NULL, '2026-07-10 14:00:00.000000', FALSE, NULL),
  (UNHEX('f3000000000070008000000000000002'), @tenant, 0, '2026-07-10 09:05:00.000000', @u_roy, '2026-07-10 09:05:00.000000', @u_roy,
   @t_review, 1, @u_hod_cse, 'HOD', 'PENDING', NULL, NULL, FALSE, NULL),
  (UNHEX('f3000000000070008000000000000003'), @tenant, 1, '2026-07-09 12:30:00.000000', @u_iyer, '2026-07-09 15:00:00.000000', @u_hod_me,
   @t_rejected, 1, @u_hod_me, 'HOD', 'REJECTED', 'The room is needed for an exam that day', '2026-07-09 15:00:00.000000', FALSE, NULL);

-- Conflict checks: each time a ticket's plan was checked against the rest of the timetable, and what was in the way.
SET @run_conflict = UNHEX('f4000000000070008000000000000001');

INSERT INTO conflict_check_run (id, tenant_id, version, created_at, created_by, updated_at, updated_by, ticket_id, check_trigger, result) VALUES
  (UNHEX('f4000000000070008000000000000002'), @tenant, 0, '2026-07-08 10:00:04.000000', @u_rao,   '2026-07-08 10:00:04.000000', @u_rao,   @t_cancel,   'APPLY',      'CLEAR'),
  (UNHEX('f4000000000070008000000000000003'), @tenant, 0, '2026-07-10 09:00:01.000000', @u_roy,   '2026-07-10 09:00:01.000000', @u_roy,   @t_review,   'SUBMISSION', 'CLEAR'),
  (@run_conflict,                              @tenant, 0, '2026-07-11 11:00:01.000000', @u_mehta, '2026-07-11 11:00:01.000000', @u_mehta, @t_conflict, 'SUBMISSION', 'BLOCKED');

INSERT INTO conflict_finding (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    run_id, ticket_id, conflict_type, message, occurrence_id, conflicting_occurrence_id, conflicting_ticket_id) VALUES
  (UNHEX('f5000000000070008000000000000001'), @tenant, 0, '2026-07-11 11:00:01.000000', @u_mehta, '2026-07-11 11:00:01.000000', @u_mehta,
   @run_conflict, @t_conflict, 'ROOM_OCCUPIED', 'R101 is already taken on 20 July from 10:00 to 11:00 by DBMS of SY-CSE-A.',
   @occ_dsa_a_0715, @occ_dbms_a_0720, NULL);

-- Notifications: one row for each person told. A person is told once of an event, whatever its kind: the unique key on
-- (tenant, source event, recipient, type) refuses a repeat.
INSERT INTO notification (id, tenant_id, version, created_at, created_by, updated_at, updated_by,
    recipient_user_id, notification_type, ticket_id, issue_id, actor_user_id, source_event_id, read_at) VALUES
  (UNHEX('f6000000000070008000000000000001'), @tenant, 1, '2026-07-08 10:00:06.000000', @system, '2026-07-08 12:00:00.000000', @system,
   @u_cr_cse_a, 'TICKET_APPLIED',    @t_cancel,   NULL, @u_rao,   UNHEX('f7000000000070008000000000000001'), '2026-07-08 12:00:00.000000'),
  (UNHEX('f6000000000070008000000000000002'), @tenant, 0, '2026-07-08 10:00:06.000000', @system, '2026-07-08 10:00:06.000000', @system,
   @u_hod_cse,  'TICKET_APPLIED',    @t_cancel,   NULL, @u_rao,   UNHEX('f7000000000070008000000000000001'), NULL),
  (UNHEX('f6000000000070008000000000000003'), @tenant, 0, '2026-07-10 09:05:01.000000', @system, '2026-07-10 09:05:01.000000', @system,
   @u_cr_cse_a, 'REVIEW_REQUESTED',  @t_review,   NULL, @u_roy,   UNHEX('f7000000000070008000000000000002'), '2026-07-10 13:00:00.000000'),
  (UNHEX('f6000000000070008000000000000004'), @tenant, 0, '2026-07-10 09:05:01.000000', @system, '2026-07-10 09:05:01.000000', @system,
   @u_hod_cse,  'REVIEW_REQUESTED',  @t_review,   NULL, @u_roy,   UNHEX('f7000000000070008000000000000002'), NULL),
  (UNHEX('f6000000000070008000000000000005'), @tenant, 0, '2026-07-10 14:00:01.000000', @system, '2026-07-10 14:00:01.000000', @system,
   @u_roy,      'REVIEW_APPROVED',   @t_review,   NULL, @u_cr_cse_a, UNHEX('f7000000000070008000000000000003'), NULL),
  (UNHEX('f6000000000070008000000000000006'), @tenant, 0, '2026-07-11 11:00:02.000000', @system, '2026-07-11 11:00:02.000000', @system,
   @u_mehta,    'TICKET_CONFLICTED', @t_conflict, NULL, NULL,     UNHEX('f7000000000070008000000000000004'), NULL),
  (UNHEX('f6000000000070008000000000000007'), @tenant, 0, '2026-07-12 09:00:01.000000', @system, '2026-07-12 09:00:01.000000', @system,
   @u_roy,      'TICKET_WITHDRAWN',  @t_withdrawn, NULL, @u_rao,  UNHEX('f7000000000070008000000000000005'), NULL),
  (UNHEX('f6000000000070008000000000000008'), @tenant, 1, '2026-07-09 15:00:01.000000', @system, '2026-07-09 16:00:00.000000', @system,
   @u_iyer,     'REVIEW_REJECTED',   @t_rejected, NULL, @u_hod_me, UNHEX('f7000000000070008000000000000006'), '2026-07-09 16:00:00.000000');
