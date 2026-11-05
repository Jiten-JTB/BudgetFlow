package com.budgetflow.account.application.port;

import java.time.Instant;
import java.util.UUID;

/** Serialises work per operation key and remembers compensated-before-applied operations (tombstones). */
public interface OperationRegistry {

    /** Transaction-scoped lock on the key (released at commit/rollback). */
    void lock(String operationKey);

    boolean isCancelled(String operationKey);

    void cancel(String operationKey, UUID userId, Instant now);
}
