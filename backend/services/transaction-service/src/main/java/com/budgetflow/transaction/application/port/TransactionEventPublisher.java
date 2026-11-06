package com.budgetflow.transaction.application.port;

import com.budgetflow.transaction.domain.Transaction;
import java.util.UUID;

/** Integration events; implementations must enlist in the current transaction (outbox). */
public interface TransactionEventPublisher {

    /**
     * Starts the RecordTransaction saga in orchestrator-service.
     *
     * @param parentCategoryId parent of the transaction's category (null if none), so budgets on the parent count it
     */
    void requested(Transaction transaction, UUID parentCategoryId);

    void completed(Transaction transaction);

    void cancelled(Transaction transaction);
}
