package com.budgetflow.orchestrator.domain;

/** Must match ck_saga_event_type in db/schema.sql. */
public enum SagaEventType {
    STARTED, COMMAND_SENT, COMMAND_RESENT, REPLY_SUCCESS, REPLY_FAILURE, STALE_REPLY_IGNORED, TIMED_OUT,
    COMPLETED, COMPENSATED, FAILED
}
