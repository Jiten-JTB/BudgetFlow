-- =============================================================================
-- identity_db — SOURCE OF TRUTH for the identity-service schema.
-- Pre-production this file is packaged as Flyway migration V1__baseline.sql.
-- After go-live: add Flyway V2+ migrations AND keep this file equal to the result.
-- Rebuild from scratch: run db/reset.sql, then this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                    UUID         PRIMARY KEY,                -- UUIDv7; also the JWT "sub"
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,                   -- {argon2}...
    display_name          VARCHAR(100) NOT NULL,
    base_currency         CHAR(3)      NOT NULL,                   -- ISO 4217
    timezone              VARCHAR(64)  NOT NULL,                   -- IANA zone id
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified_at     TIMESTAMPTZ,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,          -- optimistic locking
    CONSTRAINT ck_users_email_normalised CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_base_currency CHECK (base_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE UNIQUE INDEX ux_users_email ON users (email);

-- One-time email verification links: only the SHA-256 of the token is stored.
CREATE TABLE email_verification_token (
    token_hash  CHAR(64)    PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_email_verification_token_user ON email_verification_token (user_id, created_at DESC);

-- Emails queued in the same transaction as their cause; the relay delivers, then deletes them.
CREATE TABLE email_outbox (
    id              UUID         PRIMARY KEY,
    recipient       VARCHAR(254) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    body_text       TEXT         NOT NULL,
    body_html       TEXT,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_email_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at);
CREATE INDEX ix_email_outbox_recipient ON email_outbox (recipient);

-- -----------------------------------------------------------------------------
-- Security audit trail (append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE auth_audit_event (
    id             UUID         PRIMARY KEY,
    user_id        UUID,                                           -- null when the principal is unknown
    principal      VARCHAR(254),                                   -- submitted login name
    event_type     VARCHAR(50)  NOT NULL,
    remote_address VARCHAR(45),
    user_agent     VARCHAR(512),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auth_audit_event_type CHECK (event_type IN (
        'USER_REGISTERED', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_BLOCKED_LOCKED',
        'LOGIN_BLOCKED_DISABLED', 'ACCOUNT_LOCKED', 'PROFILE_UPDATED',
        'EMAIL_VERIFIED', 'LOGIN_BLOCKED_UNVERIFIED', 'USER_DELETED'))
);

CREATE INDEX ix_auth_audit_event_user ON auth_audit_event (user_id, occurred_at DESC);

-- -----------------------------------------------------------------------------
-- Spring Authorization Server 7.1 (JDBC stores), adapted for PostgreSQL:
-- blob -> text, timestamp -> timestamptz. Hash indexes: tokens are long, lookups are equality-only.
-- -----------------------------------------------------------------------------
CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_oauth2_registered_client_client_id ON oauth2_registered_client (client_id);

CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMPTZ   DEFAULT NULL,
    access_token_expires_at       TIMESTAMPTZ   DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMPTZ   DEFAULT NULL,
    user_code_expires_at          TIMESTAMPTZ   DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMPTZ   DEFAULT NULL,
    device_code_expires_at        TIMESTAMPTZ   DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_oauth2_authorization_state         ON oauth2_authorization USING hash (state);
CREATE INDEX ix_oauth2_authorization_code          ON oauth2_authorization USING hash (authorization_code_value);
CREATE INDEX ix_oauth2_authorization_access_token  ON oauth2_authorization USING hash (access_token_value);
CREATE INDEX ix_oauth2_authorization_refresh_token ON oauth2_authorization USING hash (refresh_token_value);
CREATE INDEX ix_oauth2_authorization_id_token      ON oauth2_authorization USING hash (oidc_id_token_value);
CREATE INDEX ix_oauth2_authorization_principal     ON oauth2_authorization (registered_client_id, principal_name);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

-- -----------------------------------------------------------------------------
-- Messaging (common-messaging): transactional outbox + idempotent inbox
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_message (
    id             UUID         PRIMARY KEY,                       -- UUIDv7, publish order
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(200) NOT NULL,
    message_type   VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100),
    aggregate_id   VARCHAR(100),
    correlation_id UUID,
    payload        JSON         NOT NULL,                          -- JSON (not JSONB): relayed byte-for-byte
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT
);

CREATE INDEX ix_outbox_message_pending ON outbox_message (id) WHERE published_at IS NULL;

CREATE TABLE inbox_message (
    message_id  UUID         NOT NULL,
    consumer    VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (message_id, consumer)
);
