package com.budgetflow.account.application;

import com.budgetflow.account.domain.BalanceEntry;
import java.util.List;
import java.util.UUID;

/** @param nextCursor pass as {@code before} for the next page; null on the last page */
public record EntryPage(List<BalanceEntry> items, UUID nextCursor) {}
