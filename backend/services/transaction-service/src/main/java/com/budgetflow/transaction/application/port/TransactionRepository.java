package com.budgetflow.transaction.application.port;

import com.budgetflow.transaction.application.TransactionCursor;
import com.budgetflow.transaction.application.TransactionQuery;
import com.budgetflow.transaction.domain.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {

    /** @return false if (user, idempotency key) already exists; nothing is written then */
    boolean insert(Transaction transaction);

    /** Optimistic; @throws org.springframework.dao.OptimisticLockingFailureException */
    void update(Transaction transaction);

    Optional<Transaction> findOwned(UUID userId, UUID transactionId);

    Optional<Transaction> findByIdempotencyKey(UUID userId, String idempotencyKey);

    /** Newest first by (occurredOn, id); {@code after} is an exclusive keyset cursor or null. */
    List<Transaction> search(UUID userId, TransactionQuery query, TransactionCursor after, int limit);
}
