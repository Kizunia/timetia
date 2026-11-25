-- Job locks (ShedLock): a scheduled job takes its row so that only one application instance runs it (ADR 0031).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Events a handler has already processed, so a repeated delivery is skipped (ADR 0031). The row is written in the
-- handler's own transaction: when the handler fails, the row is rolled back and a retry runs the handler again.
CREATE TABLE processed_event (
    event_id     BINARY(16)   NOT NULL,
    handler      VARCHAR(255) NOT NULL,
    tenant_id    BINARY(16)   NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id, handler),
    INDEX processed_event_by_time_idx (processed_at)
);
