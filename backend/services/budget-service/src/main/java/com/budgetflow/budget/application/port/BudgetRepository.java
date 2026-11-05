package com.budgetflow.budget.application.port;

import com.budgetflow.budget.domain.Budget;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository {

    /** @throws com.budgetflow.common.core.error.DomainException DUPLICATE_BUDGET */
    void insert(Budget budget);

    void update(Budget budget);

    Optional<Budget> findOwned(UUID userId, UUID budgetId);

    List<Budget> list(UUID userId, boolean includeArchived);

    /** Active budgets in {@code currency} started by {@code date}, scoped to any of {@code categoryIds} or to all expenses. Ordered by id. */
    List<Budget> findMatching(UUID userId, Currency currency, LocalDate date, Collection<UUID> categoryIds);
}
