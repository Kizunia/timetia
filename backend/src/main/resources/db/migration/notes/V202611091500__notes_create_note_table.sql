-- The reference feature (milestone 2): a table that carries every persistence convention of the foundation.
CREATE TABLE note (
    id            BINARY(16)    NOT NULL,
    tenant_id     BINARY(16)    NOT NULL,
    version       BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    created_by    BINARY(16)    NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    updated_by    BINARY(16)    NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    -- 1 while the note is live and NULL once deleted (ADR 0034). MySQL treats NULLs as distinct in a unique key, so
    -- this lets a deleted note's title be used again while two live notes of one owner cannot share it.
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    owner_id      BINARY(16)    NOT NULL,
    title         VARCHAR(120)  NOT NULL,
    body          VARCHAR(4000) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_note_owner_title (tenant_id, owner_id, title, active_marker),
    INDEX note_by_owner_idx (tenant_id, owner_id, created_at),
    INDEX note_by_deleted_idx (deleted_at)
);
