-- Metadata of a stored file (ADR 0029). The bytes live in object storage under
-- <tenant>/<category>/<file id>/<rendition>; this row says who owns the file, which renditions exist and when it was deleted.
-- Soft-deleted like every business table (ADR 0034): the retention job removes the objects and then the row.
CREATE TABLE stored_file (
    id           BINARY(16)   NOT NULL,
    tenant_id    BINARY(16)   NOT NULL,
    version      BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    created_by   BINARY(16)   NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    updated_by   BINARY(16)   NOT NULL,
    deleted_at   DATETIME(6)  NULL,
    owner_id     BINARY(16)   NOT NULL,
    category     VARCHAR(40)  NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    -- The names of the stored renditions, separated by commas, for example "small,large".
    renditions   VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX stored_file_by_owner_idx (tenant_id, owner_id),
    INDEX stored_file_by_deleted_idx (deleted_at)
);
