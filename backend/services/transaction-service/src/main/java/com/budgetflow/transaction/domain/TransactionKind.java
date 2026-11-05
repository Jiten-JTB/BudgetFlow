package com.budgetflow.transaction.domain;

/** Must match ck_money_transaction_kind in db/schema.sql. */
public enum TransactionKind {
    INCOME, EXPENSE, TRANSFER
}
