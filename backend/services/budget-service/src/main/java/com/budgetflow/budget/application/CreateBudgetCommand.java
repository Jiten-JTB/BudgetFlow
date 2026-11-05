package com.budgetflow.budget.application;

import java.util.UUID;

/**
 * @param categoryId            null = all expenses
 * @param alertThresholdPercent null = 80
 * @param startsOn              null = start of the current period
 */
public record CreateBudgetCommand(String name, UUID categoryId, String period, String limit, String currency,
                                  Integer alertThresholdPercent, String startsOn) {}
