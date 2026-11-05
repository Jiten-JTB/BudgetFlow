package com.budgetflow.budget.application;

/** {@code null} fields are left unchanged. */
public record UpdateBudgetCommand(String name, String limit, Integer alertThresholdPercent) {}
