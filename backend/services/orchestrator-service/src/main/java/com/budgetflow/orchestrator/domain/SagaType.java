package com.budgetflow.orchestrator.domain;

/** Must match ck_saga_instance_type in db/schema.sql. */
public enum SagaType {
    RECORD_TRANSACTION,
    TRANSFER_BETWEEN_ACCOUNTS;

    public static SagaType forTransactionKind(String kind) {
        return switch (kind == null ? "" : kind) {
            case "INCOME", "EXPENSE" -> RECORD_TRANSACTION;
            case "TRANSFER" -> TRANSFER_BETWEEN_ACCOUNTS;
            default -> throw new IllegalArgumentException("No saga for transaction kind " + kind);
        };
    }
}
