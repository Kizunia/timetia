-- Phase C, slice 3: an extra lecture. A ticket can add one lecture of a teaching assignment on one date. There is no lecture
-- to change yet, so the change names the teaching assignment (teaching_assignment_id) and has no source lecture; once the
-- ticket is applied, created_occurrence_id is the lecture it made.

ALTER TABLE timetable_change_ticket DROP CHECK ck_ticket_type;
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT ck_ticket_type CHECK (ticket_type IN ('RESCHEDULE', 'CANCEL', 'SUBSTITUTE', 'EXTRA'));

ALTER TABLE ticket_change_item DROP CHECK ck_change_operation;
ALTER TABLE ticket_change_item DROP CHECK ck_change_move;
ALTER TABLE ticket_change_item DROP CHECK ck_change_cancel;
ALTER TABLE ticket_change_item DROP CHECK ck_change_reassign;

ALTER TABLE ticket_change_item
    MODIFY source_occurrence_id BINARY(16) NULL,
    ADD COLUMN teaching_assignment_id BINARY(16) NULL,
    ADD COLUMN created_occurrence_id BINARY(16) NULL;

CREATE INDEX ticket_change_by_created_idx ON ticket_change_item (tenant_id, created_occurrence_id);

ALTER TABLE ticket_change_item
    ADD CONSTRAINT ck_change_operation CHECK (operation_type IN ('MOVE', 'CANCEL', 'REASSIGN_FACULTY', 'CREATE_EXTRA')),
    -- Every change but an extra lecture changes a lecture that exists, and says which (data model F-B7).
    ADD CONSTRAINT ck_change_source CHECK (
        (operation_type = 'CREATE_EXTRA' AND source_occurrence_id IS NULL AND teaching_assignment_id IS NOT NULL)
        OR (operation_type <> 'CREATE_EXTRA' AND source_occurrence_id IS NOT NULL AND teaching_assignment_id IS NULL
            AND created_occurrence_id IS NULL)),
    -- A move or an extra lecture says where to, and an in-person lecture has a room and an online one has none
    -- (data model F-B7, F-D3).
    ADD CONSTRAINT ck_change_move CHECK (operation_type NOT IN ('MOVE', 'CREATE_EXTRA') OR (
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
