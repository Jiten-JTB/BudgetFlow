package com.budgetflow.account.domain;

/** Must match ck_balance_entry_kind in db/schema.sql. */
public enum EntryKind {
    OPENING, TRANSACTION, TRANSFER, ADJUSTMENT, REVERSAL
}
