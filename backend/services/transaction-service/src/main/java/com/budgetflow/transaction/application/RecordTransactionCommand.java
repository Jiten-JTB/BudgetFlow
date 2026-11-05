package com.budgetflow.transaction.application;

import java.util.UUID;

/** Raw request values; {@link #toString()} is the canonical form hashed for idempotency-key reuse detection. */
public record RecordTransactionCommand(
        String kind, UUID accountId, UUID counterAccountId, String amount, String currency, UUID categoryId,
        UUID incomeSourceId, String occurredOn, String description, String payee) {}
