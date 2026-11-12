-- =============================================================================
-- account_db — SOURCE OF TRUTH for the account-service schema.
-- Pre-production this file is packaged as Flyway migration V1__baseline.sql.
-- After go-live: add Flyway V2+ migrations AND keep this file equal to the result.
-- Rebuild from scratch: run db/reset.sql, then this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Accounts. balance is a cached projection of balance_entry, updated in the same transaction.
-- -----------------------------------------------------------------------------
CREATE TABLE account (
    id         UUID          PRIMARY KEY,                          -- UUIDv7
    user_id    UUID          NOT NULL,                             -- owner (JWT sub)
    name       VARCHAR(100)  NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    currency   CHAR(3)       NOT NULL,                             -- ISO 4217; fixed for the account's life
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_type CHECK (type IN ('BANK', 'CASH', 'CREDIT_CARD', 'WALLET', 'SAVINGS', 'INVESTMENT', 'LOAN')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_account_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_account_user ON account (user_id, status);
-- Active account names are unique per user (case-insensitive); archived names can be reused.
CREATE UNIQUE INDEX ux_account_user_active_name ON account (user_id, lower(name)) WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------------
-- Balance ledger (append-only). Every balance change is one row; corrections are REVERSAL rows.
-- -----------------------------------------------------------------------------
CREATE TABLE balance_entry (
    id                UUID          PRIMARY KEY,                   -- UUIDv7, ledger order
    account_id        UUID          NOT NULL REFERENCES account (id),
    user_id           UUID          NOT NULL,
    operation_key     VARCHAR(150)  NOT NULL,                      -- idempotency key, e.g. txn:<id>:apply
    kind              VARCHAR(20)   NOT NULL,
    amount            NUMERIC(19,4) NOT NULL,                      -- signed: + credit, - debit
    balance_after     NUMERIC(19,4) NOT NULL,
    reference_id      UUID,                                        -- transaction / transfer id
    reverses_entry_id UUID          REFERENCES balance_entry (id),
    description       VARCHAR(200),
    created_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_balance_entry_kind CHECK (kind IN ('OPENING', 'TRANSACTION', 'TRANSFER', 'ADJUSTMENT', 'REVERSAL')),
    CONSTRAINT ck_balance_entry_amount_nonzero CHECK (amount <> 0),
    CONSTRAINT ck_balance_entry_reversal CHECK ((kind = 'REVERSAL') = (reverses_entry_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_balance_entry_operation_key ON balance_entry (operation_key);
CREATE UNIQUE INDEX ux_balance_entry_reverses ON balance_entry (reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;
CREATE INDEX ix_balance_entry_account ON balance_entry (account_id, id DESC);

-- -----------------------------------------------------------------------------
-- Cancelled operations. A compensation that arrives before (or instead of) its operation leaves a
-- tombstone, so a late-arriving original can never apply afterwards.
-- -----------------------------------------------------------------------------
CREATE TABLE operation_tombstone (
    operation_key VARCHAR(150) PRIMARY KEY,
    user_id       UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
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
