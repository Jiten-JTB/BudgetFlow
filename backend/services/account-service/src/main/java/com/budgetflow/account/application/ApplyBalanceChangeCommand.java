package com.budgetflow.account.application;

import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.money.Money;
import java.util.UUID;

/**
 * @param amount       signed: negative debits (expenses), positive credits (income)
 * @param operationKey caller-chosen idempotency key, e.g. {@code txn:<transactionId>:apply}
 */
public record ApplyBalanceChangeCommand(
        UUID userId, UUID accountId, Money amount, EntryKind kind, String operationKey, UUID referenceId,
        String description) {}
