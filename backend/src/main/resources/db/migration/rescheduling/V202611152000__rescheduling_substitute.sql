-- Phase C, slice 3: a substitute. A ticket can put another faculty member in front of one lecture, in the same slot. The
-- change keeps who takes the lecture (target_faculty_id) and, once applied, who taught it before (before_faculty_id).

ALTER TABLE timetable_change_ticket DROP CHECK ck_ticket_type;
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT ck_ticket_type CHECK (ticket_type IN ('RESCHEDULE', 'CANCEL', 'SUBSTITUTE'));

ALTER TABLE ticket_change_item DROP CHECK ck_change_operation;
ALTER TABLE ticket_change_item DROP CHECK ck_change_move;
ALTER TABLE ticket_change_item DROP CHECK ck_change_cancel;

ALTER TABLE ticket_change_item
    MODIFY operation_type VARCHAR(20) NOT NULL,
    ADD COLUMN target_faculty_id BINARY(16) NULL,
    ADD COLUMN before_faculty_id BINARY(16) NULL;

ALTER TABLE ticket_change_item
    ADD CONSTRAINT ck_change_operation CHECK (operation_type IN ('MOVE', 'CANCEL', 'REASSIGN_FACULTY')),
    -- A move says where to, and an in-person lecture has a room and an online one has none (data model F-B7, F-D3).
    ADD CONSTRAINT ck_change_move CHECK (operation_type <> 'MOVE' OR (
        target_date IS NOT NULL AND target_start_time IS NOT NULL AND target_end_time IS NOT NULL
        AND target_faculty_id IS NULL
        AND ((target_mode = 'IN_PERSON' AND target_room_id IS NOT NULL)
             OR (target_mode = 'ONLINE' AND target_room_id IS NULL)))),
    -- A cancel gives its reason and no destination (data model F-B5, F-B7).
    ADD CONSTRAINT ck_change_cancel CHECK (operation_type <> 'CANCEL' OR (
        reason IS NOT NULL AND target_date IS NULL AND target_start_time IS NULL AND target_end_time IS NULL
        AND target_mode IS NULL AND target_room_id IS NULL AND target_meeting_link IS NULL
        AND target_faculty_id IS NULL)),
    -- A substitute names who takes the lecture and no destination: the time and the room are kept (data model F-B7).
    ADD CONSTRAINT ck_change_reassign CHECK (operation_type <> 'REASSIGN_FACULTY' OR (
        target_faculty_id IS NOT NULL AND target_date IS NULL AND target_start_time IS NULL AND target_end_time IS NULL
        AND target_mode IS NULL AND target_room_id IS NULL AND target_meeting_link IS NULL));
