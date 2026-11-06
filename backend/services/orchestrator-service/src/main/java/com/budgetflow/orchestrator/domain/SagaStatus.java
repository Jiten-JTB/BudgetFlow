package com.budgetflow.orchestrator.domain;

/** Must match ck_saga_instance_status in db/schema.sql. */
public enum SagaStatus {
    RUNNING, COMPENSATING, COMPLETED, COMPENSATED,
    /** Compensation itself failed: needs manual intervention. */
    FAILED;

    public boolean isActive() {
        return this == RUNNING || this == COMPENSATING;
    }
}
