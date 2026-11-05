package com.budgetflow.budget.domain;

import java.time.LocalDate;

/** Inclusive calendar date range. */
public record PeriodRange(LocalDate start, LocalDate end) {}
