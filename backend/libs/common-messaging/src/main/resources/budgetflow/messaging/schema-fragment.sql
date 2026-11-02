-- Messaging tables required by common-messaging.
-- Copy into every service's db/schema.sql (that file stays the source of truth).

CREATE TABLE IF NOT EXISTS outbox_message (
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

CREATE INDEX IF NOT EXISTS ix_outbox_message_pending
    ON outbox_message (id) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS inbox_message (
    message_id  UUID         NOT NULL,
    consumer    VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (message_id, consumer)
);
