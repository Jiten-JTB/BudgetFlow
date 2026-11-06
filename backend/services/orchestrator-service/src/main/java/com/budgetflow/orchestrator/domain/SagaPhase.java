package com.budgetflow.orchestrator.domain;

/** Must match ck_saga_instance_phase in db/schema.sql. */
public enum SagaPhase {
    FORWARD, COMPENSATE, CANCEL, DONE
}
