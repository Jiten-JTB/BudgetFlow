package com.budgetflow.budget.application.port;

import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetUsage;
import com.budgetflow.budget.domain.PeriodRange;
import java.time.Instant;
import java.util.Optional;

public interface UsageRepository {

    /** Creates the period row if missing, then row-locks it until the transaction ends. */
    BudgetUsage lockOrCreate(Budget budget, PeriodRange period, Instant now);

    Optional<BudgetUsage> find(Budget budget, PeriodRange period);

    void update(BudgetUsage usage);
}
