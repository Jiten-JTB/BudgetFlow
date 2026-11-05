package com.budgetflow.budget.domain;

import com.budgetflow.common.core.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Spending against one budget in one period. */
public final class BudgetUsage {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UUID budgetId;
    private final PeriodRange period;
    private Money spent;
    private Instant thresholdNotifiedAt;
    private Instant exceededNotifiedAt;
    private Instant updatedAt;

    private BudgetUsage(UUID budgetId, PeriodRange period, Money spent, Instant thresholdNotifiedAt,
                        Instant exceededNotifiedAt, Instant updatedAt) {
        this.budgetId = budgetId;
        this.period = period;
        this.spent = spent;
        this.thresholdNotifiedAt = thresholdNotifiedAt;
        this.exceededNotifiedAt = exceededNotifiedAt;
        this.updatedAt = updatedAt;
    }

    public static BudgetUsage start(Budget budget, PeriodRange period, Instant now) {
        return new BudgetUsage(budget.id(), period, Money.of(BigDecimal.ZERO, budget.currency()), null, null, now);
    }

    public static BudgetUsage rehydrate(UUID budgetId, PeriodRange period, Money spent, Instant thresholdNotifiedAt,
                                        Instant exceededNotifiedAt, Instant updatedAt) {
        return new BudgetUsage(budgetId, period, spent, thresholdNotifiedAt, exceededNotifiedAt, updatedAt);
    }

    /** @param delta positive to consume, negative to release; only consumption can raise alerts */
    public UsageChange add(Money delta, Budget budget, Instant now) {
        spent = spent.plus(delta);
        updatedAt = now;
        if (!delta.isPositive()) {
            return new UsageChange(false, false);
        }
        Money threshold = Money.of(budget.limit().amount()
                .multiply(BigDecimal.valueOf(budget.alertThresholdPercent())).divide(HUNDRED), budget.currency());
        boolean thresholdReached = thresholdNotifiedAt == null && spent.compareTo(threshold) >= 0;
        boolean exceeded = exceededNotifiedAt == null && spent.isGreaterThan(budget.limit());
        if (thresholdReached) {
            thresholdNotifiedAt = now;
        }
        if (exceeded) {
            exceededNotifiedAt = now;
        }
        return new UsageChange(thresholdReached, exceeded);
    }

    public UUID budgetId() { return budgetId; }
    public PeriodRange period() { return period; }
    public Money spent() { return spent; }
    public Instant thresholdNotifiedAt() { return thresholdNotifiedAt; }
    public Instant exceededNotifiedAt() { return exceededNotifiedAt; }
    public Instant updatedAt() { return updatedAt; }
}
