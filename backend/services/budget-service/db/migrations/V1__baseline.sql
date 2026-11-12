-- =============================================================================
-- budget_db — SOURCE OF TRUTH for the budget-service schema.
-- Pre-production this file is packaged as Flyway migration V1__baseline.sql.
-- After go-live: add Flyway V2+ migrations AND keep this file equal to the result.
-- Rebuild from scratch: run db/reset.sql, then this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Budgets: a spending limit per calendar period, for one category or (category_id NULL) all expenses.
-- Soft limits: exceeding never blocks a transaction, it raises events.
-- -----------------------------------------------------------------------------
CREATE TABLE budget (
    id                      UUID          PRIMARY KEY,
    user_id                 UUID          NOT NULL,
    name                    VARCHAR(100)  NOT NULL,
    category_id             UUID,                                  -- transaction-service id; NULL = all expenses
    period                  VARCHAR(10)   NOT NULL,
    limit_amount            NUMERIC(19,4) NOT NULL,
    currency                CHAR(3)       NOT NULL,
    alert_threshold_percent SMALLINT      NOT NULL DEFAULT 80,
    starts_on               DATE          NOT NULL,                -- expenses before this date never count
    archived_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_budget_period CHECK (period IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT ck_budget_limit CHECK (limit_amount > 0),
    CONSTRAINT ck_budget_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_budget_threshold CHECK (alert_threshold_percent BETWEEN 1 AND 100)
);

CREATE INDEX ix_budget_user ON budget (user_id) WHERE archived_at IS NULL;
-- One active budget per (category or overall, period, currency).
CREATE UNIQUE INDEX ux_budget_user_active_scope ON budget
    (user_id, COALESCE(category_id, '00000000-0000-0000-0000-000000000000'::uuid), period, currency)
    WHERE archived_at IS NULL;

-- -----------------------------------------------------------------------------
-- Usage per budget per period. spent is a cached projection of budget_consumption.
-- *_notified_at make alerts fire once per period.
-- -----------------------------------------------------------------------------
CREATE TABLE budget_period_usage (
    budget_id             UUID          NOT NULL REFERENCES budget (id),
    period_start          DATE          NOT NULL,
    period_end            DATE          NOT NULL,
    spent                 NUMERIC(19,4) NOT NULL DEFAULT 0,
    threshold_notified_at TIMESTAMPTZ,
    exceeded_notified_at  TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (budget_id, period_start),
    CONSTRAINT ck_budget_period_usage_range CHECK (period_end >= period_start)
);

-- -----------------------------------------------------------------------------
-- Consumption ledger (append-only): + consume, - release (compensation).
-- -----------------------------------------------------------------------------
CREATE TABLE budget_consumption (
    id                      UUID          PRIMARY KEY,
    budget_id               UUID          NOT NULL REFERENCES budget (id),
    user_id                 UUID          NOT NULL,
    period_start            DATE          NOT NULL,
    transaction_id          UUID          NOT NULL,
    operation_key           VARCHAR(150)  NOT NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    reverses_consumption_id UUID          REFERENCES budget_consumption (id),
    created_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_budget_consumption_amount CHECK (amount <> 0),
    CONSTRAINT fk_budget_consumption_usage FOREIGN KEY (budget_id, period_start)
        REFERENCES budget_period_usage (budget_id, period_start)
);

CREATE UNIQUE INDEX ux_budget_consumption_operation ON budget_consumption (budget_id, operation_key);
CREATE UNIQUE INDEX ux_budget_consumption_reverses ON budget_consumption (reverses_consumption_id)
    WHERE reverses_consumption_id IS NOT NULL;
CREATE INDEX ix_budget_consumption_operation_key ON budget_consumption (operation_key);

-- -----------------------------------------------------------------------------
-- Saga operation log: one row per operation key, so a consume that matched no budget is still
-- idempotent, and a release that arrives first (CANCELLED) blocks a late consume.
-- -----------------------------------------------------------------------------
CREATE TABLE budget_operation (
    operation_key VARCHAR(150) PRIMARY KEY,
    user_id       UUID         NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_budget_operation_status CHECK (status IN ('APPLIED', 'REVERSED', 'CANCELLED'))
);

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
