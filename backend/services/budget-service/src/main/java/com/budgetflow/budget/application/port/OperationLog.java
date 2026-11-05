package com.budgetflow.budget.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One row per saga operation key; transaction-scoped locks serialise work per key. */
public interface OperationLog {

    enum Status { APPLIED, REVERSED, CANCELLED }

    void lock(String operationKey);

    Optional<Status> find(String operationKey);

    void record(String operationKey, UUID userId, Status status, Instant now);
}
