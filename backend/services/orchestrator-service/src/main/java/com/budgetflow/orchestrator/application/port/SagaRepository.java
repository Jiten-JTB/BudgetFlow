package com.budgetflow.orchestrator.application.port;

import com.budgetflow.orchestrator.domain.SagaInstance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persists sagas together with their drained audit events. */
public interface SagaRepository {

    /** @return false if a saga of this type already exists for the business key (nothing written) */
    boolean insertIfAbsent(SagaInstance saga);

    /** Optimistic; @throws org.springframework.dao.OptimisticLockingFailureException */
    void update(SagaInstance saga);

    /** Row-locked until the transaction ends; serialises replies for one saga. */
    Optional<SagaInstance> findForUpdate(UUID sagaId);

    /** Active sagas past their deadline, locked with SKIP LOCKED so several instances can scan safely. */
    List<SagaInstance> lockDue(Instant now, int limit);
}
