-- Spring Modulith event publication registry (the transactional outbox, ADR 0039).
-- Created by Flyway, not by Spring Modulith schema initialisation (ADR 0012).
CREATE TABLE event_publication (
    id                     BINARY(16)    NOT NULL,
    listener_id            VARCHAR(512)  NOT NULL,
    event_type             VARCHAR(512)  NOT NULL,
    serialized_event       VARCHAR(4000) NOT NULL,
    publication_date       TIMESTAMP(6)  NOT NULL,
    completion_date        TIMESTAMP(6)  NULL,
    status                 VARCHAR(20)   NULL,
    completion_attempts    INT           NULL,
    last_resubmission_date TIMESTAMP(6)  NULL,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
