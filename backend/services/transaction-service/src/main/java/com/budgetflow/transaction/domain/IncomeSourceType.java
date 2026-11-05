package com.budgetflow.transaction.domain;

/** Must match ck_income_source_type in db/schema.sql. */
public enum IncomeSourceType {
    SALARY, BUSINESS, FREELANCE, INVESTMENT, RENTAL, PENSION, GIFT, OTHER
}
