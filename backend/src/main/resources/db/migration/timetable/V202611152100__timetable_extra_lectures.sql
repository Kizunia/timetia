-- Phase C, slice 3: an extra lecture. A lecture that no entry of the weekly timetable produced is created by a ticket, on one
-- date, so it has no timetable entry. Its version is the published timetable of its division in force on that date, which
-- is what its term and, when a newer version replaces the old, whether it may be discarded are read from.

ALTER TABLE lecture_occurrence
    MODIFY timetable_entry_id BINARY(16) NULL;
