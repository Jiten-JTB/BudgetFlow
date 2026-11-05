-- =============================================================================
-- transaction_db — SOURCE OF TRUTH for the transaction-service schema.
-- Pre-production this file is packaged as Flyway migration V1__baseline.sql.
-- After go-live: add Flyway V2+ migrations AND keep this file equal to the result.
-- Rebuild from scratch: run db/reset.sql, then this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Categories (two levels: top-level and one sub-level), per user and kind.
-- -----------------------------------------------------------------------------
CREATE TABLE category (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    kind        VARCHAR(10)  NOT NULL,
    parent_id   UUID         REFERENCES category (id),
    icon        VARCHAR(40),
    color       CHAR(7),                                           -- #RRGGBB
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_category_kind CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_category_color CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$'),
    CONSTRAINT ck_category_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_category_user ON category (user_id, kind);
-- Active names are unique among siblings (same user, kind and parent), case-insensitively.
CREATE UNIQUE INDEX ux_category_user_active_name ON category
    (user_id, kind, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE archived_at IS NULL;

-- -----------------------------------------------------------------------------
-- Income sources: where money comes from (employer, business, rental, ...).
-- -----------------------------------------------------------------------------
CREATE TABLE income_source (
    id                 UUID         PRIMARY KEY,
    user_id            UUID         NOT NULL,
    name               VARCHAR(100) NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    default_account_id UUID,                                       -- account-service id; suggestion only
    notes              VARCHAR(500),
    archived_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_income_source_type CHECK (type IN (
        'SALARY', 'BUSINESS', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'PENSION', 'GIFT', 'OTHER'))
);

CREATE INDEX ix_income_source_user ON income_source (user_id);
CREATE UNIQUE INDEX ux_income_source_user_active_name ON income_source (user_id, lower(name)) WHERE archived_at IS NULL;

-- -----------------------------------------------------------------------------
-- Transactions. Created PENDING; the RecordTransaction saga moves them to COMPLETED or CANCELLED.
-- amount is always positive; kind gives the direction.
-- -----------------------------------------------------------------------------
CREATE TABLE money_transaction (
    id                 UUID          PRIMARY KEY,
    user_id            UUID          NOT NULL,
    kind               VARCHAR(10)   NOT NULL,
    status             VARCHAR(10)   NOT NULL,
    account_id         UUID          NOT NULL,                     -- account-service id
    counter_account_id UUID,                                       -- transfers: destination account
    amount             NUMERIC(19,4) NOT NULL,
    currency           CHAR(3)       NOT NULL,
    category_id        UUID          REFERENCES category (id),
    income_source_id   UUID          REFERENCES income_source (id),
    occurred_on        DATE          NOT NULL,                     -- the user's calendar date
    description        VARCHAR(200),
    payee              VARCHAR(100),
    idempotency_key    VARCHAR(100)  NOT NULL,
    request_hash       CHAR(64)      NOT NULL,                     -- sha256 of the request, detects key reuse
    saga_id            UUID,
    failure_code       VARCHAR(60),
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    completed_at       TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_money_transaction_kind CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    CONSTRAINT ck_money_transaction_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_money_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_money_transaction_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_money_transaction_shape CHECK (
        (kind = 'INCOME'   AND counter_account_id IS NULL)
     OR (kind = 'EXPENSE'  AND counter_account_id IS NULL AND income_source_id IS NULL)
     OR (kind = 'TRANSFER' AND counter_account_id IS NOT NULL AND counter_account_id <> account_id
                           AND category_id IS NULL AND income_source_id IS NULL))
);

CREATE UNIQUE INDEX ux_money_transaction_idempotency ON money_transaction (user_id, idempotency_key);
CREATE INDEX ix_money_transaction_user_date ON money_transaction (user_id, occurred_on DESC, id DESC);
CREATE INDEX ix_money_transaction_user_account ON money_transaction (user_id, account_id, occurred_on DESC);
CREATE INDEX ix_money_transaction_user_category ON money_transaction (user_id, category_id) WHERE category_id IS NOT NULL;
CREATE INDEX ix_money_transaction_pending ON money_transaction (created_at) WHERE status = 'PENDING';

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
