-- V2 (release v0.1.4): email verification and account deletion.

-- Everyone who signed up before verification existed is treated as verified, so no one is locked out.
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

-- One-time links: only the SHA-256 of the token is stored; deleting a user removes theirs.
CREATE TABLE email_verification_token (
    token_hash  CHAR(64)    PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_email_verification_token_user ON email_verification_token (user_id, created_at DESC);

-- Emails queued in the same transaction as the change that causes them; a relay delivers and then deletes them.
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

ALTER TABLE auth_audit_event DROP CONSTRAINT ck_auth_audit_event_type;
ALTER TABLE auth_audit_event ADD CONSTRAINT ck_auth_audit_event_type CHECK (event_type IN (
    'USER_REGISTERED', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_BLOCKED_LOCKED',
    'LOGIN_BLOCKED_DISABLED', 'ACCOUNT_LOCKED', 'PROFILE_UPDATED',
    'EMAIL_VERIFIED', 'LOGIN_BLOCKED_UNVERIFIED', 'USER_DELETED'));
