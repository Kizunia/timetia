-- The current avatar of a user (milestone 4, ADR 0029): the ID of a file kept by the files module, or NULL when the user
-- has none. There is no foreign key, because the files module owns its table and may be switched off; the files module
-- removes a user's files when the user is erased, and the profile row goes with it.
ALTER TABLE user_profile ADD COLUMN avatar_file_id BINARY(16) NULL;
