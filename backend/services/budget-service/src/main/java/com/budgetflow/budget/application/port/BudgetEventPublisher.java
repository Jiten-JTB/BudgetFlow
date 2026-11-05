package com.budgetflow.budget.application.port;

import com.budgetflow.budget.domain.BudgetStatus;

/** Integration events; implementations must enlist in the current transaction (outbox). */
public interface BudgetEventPublisher {

    void thresholdReached(BudgetStatus status);

    void exceeded(BudgetStatus status);
}
