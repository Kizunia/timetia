-- Phase C, slice 2: tickets sent for review. A ticket names the people asked to review it, per revision: when a rejected ticket
-- is edited and sent again its revision rises, and only the reviews of the current revision count, while the earlier ones stay
-- as history (data model F-B3).

CREATE TABLE ticket_review (
    id                       BINARY(16)   NOT NULL,
    tenant_id                BINARY(16)   NOT NULL,
    version                  BIGINT       NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    created_by               BINARY(16)   NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    updated_by               BINARY(16)   NOT NULL,
    ticket_id                BINARY(16)   NOT NULL,
    ticket_revision_no       SMALLINT     NOT NULL,
    reviewer_user_id         BINARY(16)   NOT NULL,
    reviewer_capacity        VARCHAR(20)  NOT NULL,
    review_status            VARCHAR(12)  NOT NULL,
    decision_reason          VARCHAR(500) NULL,
    decided_at               DATETIME(6)  NULL,
    -- Set when the author took a rejection to the HOD; the review points at the rejection it answers.
    is_escalation            BOOLEAN      NOT NULL DEFAULT FALSE,
    escalated_from_review_id BINARY(16)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ticket_review (tenant_id, ticket_id, ticket_revision_no, reviewer_user_id, reviewer_capacity),
    INDEX ticket_review_by_reviewer_idx (tenant_id, reviewer_user_id, review_status),
    CONSTRAINT ck_ticket_review_status CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED', 'ACKNOWLEDGED')),
    CONSTRAINT ck_ticket_review_capacity CHECK (reviewer_capacity IN
        ('HOD', 'CR', 'BR', 'AFFECTED_FACULTY', 'OTHER_FACULTY')),
    -- A rejection always says why (data model F-B5), and an answer says when it was given.
    CONSTRAINT ck_ticket_review_rejection CHECK (review_status <> 'REJECTED' OR decision_reason IS NOT NULL),
    CONSTRAINT ck_ticket_review_decided CHECK ((review_status = 'PENDING') = (decided_at IS NULL))
);

-- A check can also be made because another change was applied (see the rescheduling module).
ALTER TABLE conflict_check_run DROP CONSTRAINT ck_conflict_run_trigger;
ALTER TABLE conflict_check_run ADD CONSTRAINT ck_conflict_run_trigger
    CHECK (check_trigger IN ('SUBMISSION', 'APPLY', 'RECHECK'));
