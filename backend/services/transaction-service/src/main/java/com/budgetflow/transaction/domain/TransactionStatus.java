package com.budgetflow.transaction.domain;

/** Must match ck_money_transaction_status in db/schema.sql. */
public enum TransactionStatus {
    PENDING, COMPLETED, CANCELLED
}
