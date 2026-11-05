package com.budgetflow.budget.domain;

import com.budgetflow.common.core.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Read model: how a budget stands in one period. */
public record BudgetStatus(Budget budget, PeriodRange period, Money spent) {

    public enum State { ON_TRACK, WARNING, EXCEEDED }

    public Money remaining() {
        return budget.limit().minus(spent);
    }

    /** Floor of spent / limit * 100; can exceed 100. */
    public int percentUsed() {
        return spent.amount().multiply(BigDecimal.valueOf(100))
                .divide(budget.limit().amount(), 0, RoundingMode.FLOOR).intValueExact();
    }

    public State state() {
        if (spent.isGreaterThan(budget.limit())) {
            return State.EXCEEDED;
        }
        return percentUsed() >= budget.alertThresholdPercent() ? State.WARNING : State.ON_TRACK;
    }
}
