-- Phase C, slice 1: tickets that reschedule or cancel one lecture, applied by the author (self-approval).
--
-- A ticket copies from the lecture it concerns what cannot change (where it is, what kind it is, who teaches it), so that
-- a list can be filtered by a person's role and place in one query, as the live timetable is. The lectures themselves, and
-- what they hold, are the timetable module's: a ticket names a lecture by its ID and never reads its tables.

CREATE TABLE timetable_change_ticket (
    id                BINARY(16)    NOT NULL,
    tenant_id         BINARY(16)    NOT NULL,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    created_by        BINARY(16)    NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    updated_by        BINARY(16)    NOT NULL,
    ticket_type       VARCHAR(20)   NOT NULL,
    status            VARCHAR(12)   NOT NULL,
    approval_path     VARCHAR(10)   NULL,
    author_user_id    BINARY(16)    NOT NULL,
    -- The faculty member an HOD acts for; null when the author is the teacher.
    acting_faculty_id BINARY(16)    NULL,
    description       VARCHAR(1000) NULL,
    revision_no       SMALLINT      NOT NULL,
    -- The lecture, as it was when the ticket was raised.
    assignment_id     BINARY(16)    NOT NULL,
    department_id     BINARY(16)    NOT NULL,
    division_id       BINARY(16)    NOT NULL,
    batch_id          BINARY(16)    NULL,
    session_type      VARCHAR(10)   NOT NULL,
    faculty_id        BINARY(16)    NOT NULL,
    -- When the earliest lecture the ticket involves starts: a ticket not yet applied lapses then.
    expires_at        DATETIME(6)   NOT NULL,
    is_conflicted     BOOLEAN       NOT NULL DEFAULT FALSE,
    conflicted_since  DATETIME(6)   NULL,
    closed_at         DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX ticket_by_author_idx (tenant_id, author_user_id, status),
    INDEX ticket_by_faculty_idx (tenant_id, faculty_id, status),
    INDEX ticket_by_department_idx (tenant_id, department_id, status),
    INDEX ticket_by_division_idx (tenant_id, division_id, status),
    INDEX ticket_by_batch_idx (tenant_id, batch_id, status),
    CONSTRAINT ck_ticket_type CHECK (ticket_type IN ('RESCHEDULE', 'CANCEL')),
    CONSTRAINT ck_ticket_status CHECK (status IN
        ('DRAFT', 'IN_REVIEW', 'APPLIED', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'REVERTED')),
    CONSTRAINT ck_ticket_approval_path CHECK (approval_path IS NULL OR approval_path IN ('SELF', 'REVIEWED')),
    -- A ticket that is applied says how, and one that is over says when.
    CONSTRAINT ck_ticket_applied_path CHECK (status <> 'APPLIED' OR approval_path IS NOT NULL),
    CONSTRAINT ck_ticket_closed CHECK (
        (status IN ('APPLIED', 'WITHDRAWN', 'EXPIRED', 'REVERTED')) = (closed_at IS NOT NULL))
);

CREATE TABLE ticket_change_item (
    id                    BINARY(16)   NOT NULL,
    tenant_id             BINARY(16)   NOT NULL,
    version               BIGINT       NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    created_by            BINARY(16)   NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    updated_by            BINARY(16)   NOT NULL,
    ticket_id             BINARY(16)   NOT NULL,
    operation_type        VARCHAR(10)  NOT NULL,
    source_occurrence_id  BINARY(16)   NOT NULL,
    -- The state of the lecture the change was planned against: if the lecture has changed since, the change is refused.
    source_revision       BIGINT       NOT NULL,
    reason                VARCHAR(500) NULL,
    target_date           DATE         NULL,
    target_start_time     TIME         NULL,
    target_end_time       TIME         NULL,
    target_mode           VARCHAR(10)  NULL,
    target_room_id        BINARY(16)   NULL,
    target_meeting_link   VARCHAR(500) NULL,
    -- Where the lecture was before the change was applied, kept so that the change can be undone.
    before_date           DATE         NULL,
    before_start_time     TIME         NULL,
    before_end_time       TIME         NULL,
    before_mode           VARCHAR(10)  NULL,
    before_room_id        BINARY(16)   NULL,
    before_meeting_link   VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX ticket_change_by_ticket_idx (tenant_id, ticket_id),
    INDEX ticket_change_by_lecture_idx (tenant_id, source_occurrence_id),
    CONSTRAINT ck_change_operation CHECK (operation_type IN ('MOVE', 'CANCEL')),
    -- A move says where to, and an in-person lecture has a room and an online one has none (data model F-B7, F-D3).
    CONSTRAINT ck_change_move CHECK (operation_type <> 'MOVE' OR (
        target_date IS NOT NULL AND target_start_time IS NOT NULL AND target_end_time IS NOT NULL
        AND ((target_mode = 'IN_PERSON' AND target_room_id IS NOT NULL)
             OR (target_mode = 'ONLINE' AND target_room_id IS NULL)))),
    -- A cancel gives its reason and no destination (data model F-B5, F-B7).
    CONSTRAINT ck_change_cancel CHECK (operation_type <> 'CANCEL' OR (
        reason IS NOT NULL AND target_date IS NULL AND target_start_time IS NULL AND target_end_time IS NULL
        AND target_mode IS NULL AND target_room_id IS NULL AND target_meeting_link IS NULL))
);

-- Every time the conflicts of a ticket were checked, and what was found. The latest run is the truth; the older ones are
-- history (data model F-C2, F-C5).
CREATE TABLE conflict_check_run (
    id            BINARY(16)  NOT NULL,
    tenant_id     BINARY(16)  NOT NULL,
    version       BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    created_by    BINARY(16)  NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    updated_by    BINARY(16)  NOT NULL,
    ticket_id     BINARY(16)  NOT NULL,
    check_trigger VARCHAR(12) NOT NULL,
    result        VARCHAR(8)  NOT NULL,
    PRIMARY KEY (id),
    INDEX conflict_run_by_ticket_idx (tenant_id, ticket_id, created_at),
    CONSTRAINT ck_conflict_run_trigger CHECK (check_trigger IN ('SUBMISSION', 'APPLY')),
    CONSTRAINT ck_conflict_run_result CHECK (result IN ('CLEAR', 'BLOCKED'))
);

CREATE TABLE conflict_finding (
    id                        BINARY(16)   NOT NULL,
    tenant_id                 BINARY(16)   NOT NULL,
    version                   BIGINT       NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    created_by                BINARY(16)   NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    updated_by                BINARY(16)   NOT NULL,
    run_id                    BINARY(16)   NOT NULL,
    ticket_id                 BINARY(16)   NOT NULL,
    conflict_type             VARCHAR(20)  NOT NULL,
    message                   VARCHAR(500) NOT NULL,
    occurrence_id             BINARY(16)   NULL,
    conflicting_occurrence_id BINARY(16)   NULL,
    conflicting_ticket_id     BINARY(16)   NULL,
    PRIMARY KEY (id),
    INDEX conflict_finding_by_run_idx (tenant_id, run_id)
);
