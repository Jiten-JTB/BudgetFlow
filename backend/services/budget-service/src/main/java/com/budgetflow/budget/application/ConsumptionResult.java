package com.budgetflow.budget.application;

import com.budgetflow.budget.domain.BudgetStatus;
import java.util.List;

/** @param statuses every budget the expense counted against, after the change */
public record ConsumptionResult(List<BudgetStatus> statuses, boolean duplicate) {}
