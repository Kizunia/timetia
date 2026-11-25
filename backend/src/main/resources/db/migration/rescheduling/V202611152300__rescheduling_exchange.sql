-- Phase C, slice 3: an exchange. One ticket now carries more than one change: an exchange moves two lectures, each into the
-- other's place. A ticket's changes are numbered (item_no), and each keeps the faculty member who teaches the lecture it is
-- about (faculty_id), so the people an exchange affects are known without reading the timetable.

ALTER TABLE timetable_change_ticket DROP CHECK ck_ticket_type;
ALTER TABLE timetable_change_ticket
    ADD CONSTRAINT ck_ticket_type CHECK (ticket_type IN ('RESCHEDULE', 'CANCEL', 'SUBSTITUTE', 'EXTRA', 'EXCHANGE'));

ALTER TABLE ticket_change_item
    ADD COLUMN item_no INT NOT NULL DEFAULT 1,
    ADD COLUMN faculty_id BINARY(16) NULL;

-- Until now a ticket had one change, about the lecture of the ticket's own faculty member.
UPDATE ticket_change_item item
    JOIN timetable_change_ticket ticket ON ticket.id = item.ticket_id
SET item.faculty_id = ticket.faculty_id;

ALTER TABLE ticket_change_item
    ADD CONSTRAINT uq_ticket_change_item_no UNIQUE (tenant_id, ticket_id, item_no);

CREATE INDEX ticket_change_by_faculty_idx ON ticket_change_item (tenant_id, faculty_id);
