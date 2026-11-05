package com.budgetflow.budget.application;

import com.budgetflow.common.core.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Saga step for an expense.
 *
 * @param categoryId       the expense's category (null if uncategorised)
 * @param parentCategoryId its parent, so a budget on "Food" also counts "Food > Restaurants"
 * @param amount           positive expense amount
 * @param operationKey     idempotency key, e.g. {@code txn:<transactionId>:budget}
 */
public record ConsumeBudgetCommand(UUID userId, UUID transactionId, UUID categoryId, UUID parentCategoryId,
                                   Money amount, LocalDate occurredOn, String operationKey) {}
