package com.budgetflow.transaction.application.port;

import com.budgetflow.transaction.domain.Transaction;

/** Integration events; implementations must enlist in the current transaction (outbox). */
public interface TransactionEventPublisher {

    /** Starts the RecordTransaction saga in orchestrator-service. */
    void requested(Transaction transaction);

    void completed(Transaction transaction);

    void cancelled(Transaction transaction);
}
