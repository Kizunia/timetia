-- Phase C, slice 4: a revert. A revert is a new ticket that undoes an applied one (rules: reverts). Its changes restore each
-- lecture to where, and with whom, it was before the applied ticket changed it, so a change of operation RESTORE names the
-- slot to go back to (the target columns) and the teacher to go back to (target_faculty_id). The reverted ticket shows as
-- REVERTED and is linked to the ticket that reverted it.

ALTER TABLE timetable_change_ticket
    ADD COLUMN reverts_ticket_id BINARY(16) NULL,
    ADD COLUMN reverted_by_ticket_id BINARY(16) NULL;

ALTER TABLE timetable_change_ticket DROP CHECK ck_ticket_type;
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT ck_ticket_type CHECK (ticket_type IN
        ('RESCHEDULE', 'CANCEL', 'SUBSTITUTE', 'EXTRA', 'EXCHANGE', 'REVERT')),
    -- Only a revert undoes another ticket, and a ticket is reverted exactly when a revert of it has been applied.
    ADD CONSTRAINT ck_ticket_revert_link CHECK ((ticket_type = 'REVERT') = (reverts_ticket_id IS NOT NULL)),
    ADD CONSTRAINT ck_ticket_reverted_by CHECK ((status = 'REVERTED') = (reverted_by_ticket_id IS NOT NULL));

CREATE INDEX ticket_by_reverts_idx ON timetable_change_ticket (tenant_id, reverts_ticket_id, status);

ALTER TABLE ticket_change_item DROP CHECK ck_change_operation;
ALTER TABLE ticket_change_item
    ADD CONSTRAINT ck_change_operation CHECK (operation_type IN
        ('MOVE', 'CANCEL', 'REASSIGN_FACULTY', 'CREATE_EXTRA', 'RESTORE')),
    -- A restore names the slot and the teacher the lecture goes back to; the room and mode are those of that slot.
    ADD CONSTRAINT ck_change_restore CHECK (operation_type <> 'RESTORE' OR (
        target_date IS NOT NULL AND target_start_time IS NOT NULL AND target_end_time IS NOT NULL
        AND target_faculty_id IS NOT NULL
        AND ((target_mode = 'IN_PERSON' AND target_room_id IS NOT NULL)
             OR (target_mode = 'ONLINE' AND target_room_id IS NULL))));
