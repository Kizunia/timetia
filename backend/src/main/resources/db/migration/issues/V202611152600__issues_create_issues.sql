-- Phase D, slice 1: issues. A class representative (theory) or batch representative (labs and tutorials) opens an issue about
-- what the class wants, mentions the faculty it concerns, and the faculty who agree raise a ticket themselves (rules: issues
-- from CR and BR). An issue changes nothing in the timetable; it can be linked to the ticket that answers it.
--
-- An issue belongs to the place it was opened in: always its division and department, and for a lab or tutorial also its
-- batch. A theory issue names no batch because it is for the whole division.

CREATE TABLE issue (
    id               BINARY(16)    NOT NULL,
    tenant_id        BINARY(16)    NOT NULL,
    version          BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    created_by       BINARY(16)    NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    updated_by       BINARY(16)    NOT NULL,
    session_type     VARCHAR(10)   NOT NULL,
    status           VARCHAR(8)    NOT NULL,
    department_id    BINARY(16)    NOT NULL,
    division_id      BINARY(16)    NOT NULL,
    batch_id         BINARY(16)    NULL,
    title            VARCHAR(200)  NOT NULL,
    description      VARCHAR(2000) NULL,
    opener_user_id   BINARY(16)    NOT NULL,
    closed_at        DATETIME(6)   NULL,
    closed_by_user_id BINARY(16)   NULL,
    PRIMARY KEY (id),
    INDEX issue_by_opener_idx (tenant_id, opener_user_id, status),
    INDEX issue_by_department_idx (tenant_id, department_id, status),
    INDEX issue_by_division_idx (tenant_id, division_id, status),
    INDEX issue_by_batch_idx (tenant_id, batch_id, status),
    CONSTRAINT ck_issue_session CHECK (session_type IN ('THEORY', 'LAB', 'TUTORIAL')),
    CONSTRAINT ck_issue_status CHECK (status IN ('OPEN', 'CLOSED')),
    -- Theory is for a division and its CR; a lab or tutorial is for a batch and its BR (data model F-H1).
    CONSTRAINT ck_issue_place CHECK ((session_type = 'THEORY') = (batch_id IS NULL)),
    -- Closed exactly when it says when and by whom.
    CONSTRAINT ck_issue_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND closed_by_user_id IS NOT NULL))
);

-- The faculty an issue mentions, by faculty record: the person is told, and may close the issue.
CREATE TABLE issue_mention (
    id                   BINARY(16)  NOT NULL,
    tenant_id            BINARY(16)  NOT NULL,
    version              BIGINT      NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    created_by           BINARY(16)  NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    updated_by           BINARY(16)  NOT NULL,
    issue_id             BINARY(16)  NOT NULL,
    mentioned_faculty_id BINARY(16)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_issue_mention (tenant_id, issue_id, mentioned_faculty_id),
    INDEX issue_mention_by_faculty_idx (tenant_id, mentioned_faculty_id)
);

-- The tickets that answer an issue, as a pull request answers an issue.
CREATE TABLE issue_ticket_link (
    id               BINARY(16)  NOT NULL,
    tenant_id        BINARY(16)  NOT NULL,
    version          BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    created_by       BINARY(16)  NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    updated_by       BINARY(16)  NOT NULL,
    issue_id         BINARY(16)  NOT NULL,
    ticket_id        BINARY(16)  NOT NULL,
    linked_by_user_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_issue_ticket_link (tenant_id, issue_id, ticket_id)
);
