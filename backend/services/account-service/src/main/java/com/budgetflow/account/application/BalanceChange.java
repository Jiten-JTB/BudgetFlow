package com.budgetflow.account.application;

import com.budgetflow.account.domain.BalanceEntry;

/** @param duplicate the operation had already been applied; {@code entry} is the original result */
public record BalanceChange(BalanceEntry entry, boolean duplicate) {}
