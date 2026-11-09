-- The audit log (ADR 0046): append-only, no soft delete, trimmed only by the retention purge. People are referred to by
-- user ID only; no name or email column exists.
CREATE TABLE audit_entry (
    id          BINARY(16)    NOT NULL,
    tenant_id   BINARY(16)    NOT NULL,
    occurred_at DATETIME(6)   NOT NULL,
    actor_id    BINARY(16)    NOT NULL,
    action      VARCHAR(100)  NOT NULL,
    target_type VARCHAR(50)   NOT NULL,
    target_id   BINARY(16)    NULL,
    outcome     VARCHAR(100)  NOT NULL,
    trace_id    VARCHAR(32)   NULL,
    changes     VARCHAR(4000) NULL,
    PRIMARY KEY (id),
    INDEX audit_entry_by_time_idx (tenant_id, occurred_at),
    INDEX audit_entry_by_target_idx (tenant_id, target_type, target_id),
    INDEX audit_entry_by_actor_idx (tenant_id, actor_id)
);
