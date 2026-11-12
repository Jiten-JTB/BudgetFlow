-- =============================================================================
-- orchestrator_db — SOURCE OF TRUTH for the orchestrator-service schema.
-- Pre-production this file is packaged as Flyway migration V1__baseline.sql.
-- After go-live: add Flyway V2+ migrations AND keep this file equal to the result.
-- Rebuild from scratch: run db/reset.sql, then this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Saga instances: one persisted state machine per business operation.
-- Exactly one command is outstanding at a time (awaiting_command_id); deadline_at drives timeouts.
-- -----------------------------------------------------------------------------
CREATE TABLE saga_instance (
    id                    UUID          PRIMARY KEY,               -- UUIDv7; correlation id of every message
    saga_type             VARCHAR(40)   NOT NULL,
    business_key          VARCHAR(100)  NOT NULL,                  -- e.g. the transaction id
    user_id               UUID          NOT NULL,
    data                  JSONB         NOT NULL,                  -- immutable saga input
    status                VARCHAR(20)   NOT NULL,
    phase                 VARCHAR(20)   NOT NULL,
    step_index            INTEGER       NOT NULL,
    awaiting_command_id   UUID,
    awaiting_command_type VARCHAR(100),
    attempts              INTEGER       NOT NULL DEFAULT 0,        -- sends of the awaited command
    deadline_at           TIMESTAMPTZ,
    failure_code          VARCHAR(100),
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    finished_at           TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_saga_instance_type CHECK (saga_type IN ('RECORD_TRANSACTION', 'TRANSFER_BETWEEN_ACCOUNTS')),
    CONSTRAINT ck_saga_instance_status CHECK (status IN ('RUNNING', 'COMPENSATING', 'COMPLETED', 'COMPENSATED', 'FAILED')),
    CONSTRAINT ck_saga_instance_phase CHECK (phase IN ('FORWARD', 'COMPENSATE', 'CANCEL', 'DONE')),
    CONSTRAINT ck_saga_instance_waiting CHECK (
        (status IN ('RUNNING', 'COMPENSATING')) = (awaiting_command_id IS NOT NULL AND deadline_at IS NOT NULL))
);

-- At most one saga of a type per business operation (duplicate trigger events are ignored).
CREATE UNIQUE INDEX ux_saga_instance_business_key ON saga_instance (saga_type, business_key);
CREATE INDEX ix_saga_instance_due ON saga_instance (deadline_at) WHERE status IN ('RUNNING', 'COMPENSATING');
CREATE INDEX ix_saga_instance_failed ON saga_instance (updated_at) WHERE status = 'FAILED';

-- -----------------------------------------------------------------------------
-- Saga audit trail (append-only): every command, reply, timeout and outcome.
-- -----------------------------------------------------------------------------
CREATE TABLE saga_event (
    id           UUID         PRIMARY KEY,
    saga_id      UUID         NOT NULL REFERENCES saga_instance (id),
    event_type   VARCHAR(30)  NOT NULL,
    command_type VARCHAR(100),
    command_id   UUID,
    detail       VARCHAR(500),
    occurred_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_saga_event_type CHECK (event_type IN (
        'STARTED', 'COMMAND_SENT', 'COMMAND_RESENT', 'REPLY_SUCCESS', 'REPLY_FAILURE', 'STALE_REPLY_IGNORED',
        'TIMED_OUT', 'COMPLETED', 'COMPENSATED', 'FAILED'))
);

CREATE INDEX ix_saga_event_saga ON saga_event (saga_id, occurred_at);

-- -----------------------------------------------------------------------------
-- Messaging (common-messaging): transactional outbox + idempotent inbox
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_message (
    id             UUID         PRIMARY KEY,               -- UUIDv7, publish order
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(200) NOT NULL,
    message_type   VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100),
    aggregate_id   VARCHAR(100),
    correlation_id UUID,
    payload        JSON         NOT NULL,          -- JSON (not JSONB): relayed byte-for-byte
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX ix_outbox_message_pending
    ON outbox_message (id) WHERE published_at IS NULL;

CREATE TABLE inbox_message (
    message_id  UUID         NOT NULL,
    consumer    VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (message_id, consumer)
);
