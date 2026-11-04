package com.budgetflow.identity.domain;

/** Must match the CHECK constraint on auth_audit_event.event_type in db/schema.sql. */
public enum AuditEventType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_BLOCKED_LOCKED,
    LOGIN_BLOCKED_DISABLED,
    ACCOUNT_LOCKED,
    PROFILE_UPDATED
}
