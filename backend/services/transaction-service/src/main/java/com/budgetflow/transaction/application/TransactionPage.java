package com.budgetflow.transaction.application;

import com.budgetflow.transaction.domain.Transaction;
import java.util.List;

/** @param nextCursor opaque; null on the last page */
public record TransactionPage(List<Transaction> items, String nextCursor) {}
