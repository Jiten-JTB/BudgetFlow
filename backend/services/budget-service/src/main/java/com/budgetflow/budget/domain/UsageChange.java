package com.budgetflow.budget.domain;

/** Alerts newly due after a usage change (each fires at most once per budget period). */
public record UsageChange(boolean thresholdReached, boolean exceeded) {}
