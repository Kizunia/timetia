-- Phase C, slice 4: a permanent change. A lecture of the weekly timetable moves every week from a date onward. It is made as
-- one ticket whose changes are one MOVE for each lecture the weekly entry still has to hold from that date, so it is applied
-- all together or not at all, and a revert undoes all of it. It always needs the approval of an HOD (rules: permanent changes).

ALTER TABLE timetable_change_ticket DROP CHECK ck_ticket_type;
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT ck_ticket_type CHECK (ticket_type IN
        ('RESCHEDULE', 'CANCEL', 'SUBSTITUTE', 'EXTRA', 'EXCHANGE', 'REVERT', 'PERMANENT_CHANGE')),
    -- A permanent change is applied only after review (data model F-B6), never by its author alone.
    ADD CONSTRAINT ck_ticket_permanent_path CHECK (ticket_type <> 'PERMANENT_CHANGE' OR approval_path IS NULL
        OR approval_path = 'REVIEWED');
