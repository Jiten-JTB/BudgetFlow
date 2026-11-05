package com.budgetflow.account.domain;

/** Must match ck_account_type in db/schema.sql. */
public enum AccountType {
    BANK, CASH, CREDIT_CARD, WALLET, SAVINGS, INVESTMENT, LOAN
}
