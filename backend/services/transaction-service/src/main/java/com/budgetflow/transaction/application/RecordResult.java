package com.budgetflow.transaction.application;

import com.budgetflow.transaction.domain.Transaction;

/** @param replayed same Idempotency-Key and request seen before; {@code transaction} is the original */
public record RecordResult(Transaction transaction, boolean replayed) {}
