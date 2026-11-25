-- Test-only table that carries every persistence convention (ADR 0028, 0033, 0034). Loaded through
-- spring.flyway.locations in src/test/resources/config/application.properties; never part of the application.
-- The version is lower than any timestamped module migration, so it always runs first.
CREATE TABLE test_widget (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    created_by    BINARY(16)   NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BINARY(16)   NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    -- 1 while the row is live and NULL once deleted. MySQL treats NULLs as distinct in a unique key, so including this
    -- column lets a deleted row's name be used again while two live rows cannot share it.
    active_marker TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    name          VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_test_widget_name (tenant_id, name, active_marker)
);
