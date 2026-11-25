-- The picture attached to a note (milestone 5). Only the ID of the stored file is kept, as for an avatar: the files module
-- owns the bytes, and there is no foreign key across modules (ADR 0024, ADR 0029). NULL means no attachment.
alter table note
    add column attachment_file_id binary(16) null;
