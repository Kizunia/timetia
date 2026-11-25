-- Phase D, slice 2: in-app notifications (rules: notification triggers). One row for each person told, with what it is about
-- and when they read it. There is no channel, delivery or reminder column: this version is in-app only.
--
-- source_event_id is the event the notification came from. A person is told once of an event for each kind of message, even
-- if the event is delivered again.

CREATE TABLE notification (
    id                BINARY(16)  NOT NULL,
    tenant_id         BINARY(16)  NOT NULL,
    version           BIGINT      NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    created_by        BINARY(16)  NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    updated_by        BINARY(16)  NOT NULL,
    recipient_user_id BINARY(16)  NOT NULL,
    notification_type VARCHAR(20) NOT NULL,
    ticket_id         BINARY(16)  NULL,
    issue_id          BINARY(16)  NULL,
    actor_user_id     BINARY(16)  NULL,
    source_event_id   BINARY(16)  NOT NULL,
    read_at           DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_once (tenant_id, source_event_id, recipient_user_id, notification_type),
    INDEX notification_by_recipient_idx (tenant_id, recipient_user_id, read_at, created_at),
    CONSTRAINT ck_notification_type CHECK (notification_type IN
        ('REVIEW_REQUESTED', 'REVIEW_APPROVED', 'REVIEW_REJECTED', 'TICKET_APPLIED', 'TICKET_CONFLICTED', 'TICKET_EDITED',
         'TICKET_WITHDRAWN', 'TICKET_REVERTED', 'ISSUE_MENTION')),
    -- It is about a ticket or an issue (data model F-I1).
    CONSTRAINT ck_notification_subject CHECK (ticket_id IS NOT NULL OR issue_id IS NOT NULL)
);
