package com.budgetflow.account.domain;

import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.util.UUID;

/** One immutable ledger line. {@code amount} is signed: positive credits, negative debits. */
public record BalanceEntry(
        UUID id,
        UUID accountId,
        UUID userId,
        String operationKey,
        EntryKind kind,
        Money amount,
        Money balanceAfter,
        UUID referenceId,
        UUID reversesEntryId,
        String description,
        Instant createdAt) {}
