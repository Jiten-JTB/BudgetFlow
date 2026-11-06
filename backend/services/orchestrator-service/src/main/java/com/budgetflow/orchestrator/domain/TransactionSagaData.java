package com.budgetflow.orchestrator.domain;

import java.util.UUID;

/** Saga input, taken from transaction.TransactionRequested. {@code amount} is a positive decimal string. */
public record TransactionSagaData(
        UUID transactionId, UUID userId, String kind, UUID accountId, UUID counterAccountId, String amount,
        String currency, UUID categoryId, UUID parentCategoryId, String occurredOn) {}
