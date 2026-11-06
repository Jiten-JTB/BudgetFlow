package com.budgetflow.transaction.application;

import com.budgetflow.transaction.domain.TransactionKind;
import com.budgetflow.transaction.domain.TransactionStatus;
import java.time.LocalDate;
import java.util.UUID;

/** All filters optional; dates are inclusive; a category also matches its sub-categories. */
public record TransactionQuery(LocalDate from, LocalDate to, TransactionKind kind, TransactionStatus status,
                               UUID accountId, UUID categoryId, UUID incomeSourceId) {}
