package com.budgetflow.orchestrator.application;

import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.orchestrator.application.port.CommandBus;
import com.budgetflow.orchestrator.application.port.SagaRepository;
import com.budgetflow.orchestrator.domain.PendingCommand;
import com.budgetflow.orchestrator.domain.SagaInstance;
import com.budgetflow.orchestrator.domain.SagaPolicy;
import com.budgetflow.orchestrator.domain.SagaStatus;
import com.budgetflow.orchestrator.domain.SagaType;
import com.budgetflow.orchestrator.domain.TransactionSagaData;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Drives saga state machines: each state change and the next command commit in one transaction. */
public class SagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SagaCoordinator.class);

    private final SagaRepository sagas;
    private final CommandBus commands;
    private final SagaPolicy policy;
    private final Clock clock;

    public SagaCoordinator(SagaRepository sagas, CommandBus commands, SagaPolicy policy, Clock clock) {
        this.sagas = sagas;
        this.commands = commands;
        this.policy = policy;
        this.clock = clock;
    }

    /** @return the new saga id, or empty if a saga for this transaction already exists */
    @Transactional
    public Optional<UUID> startForTransaction(TransactionSagaData data) {
        Instant now = clock.instant();
        SagaInstance saga = SagaInstance.create(UuidV7.generate(), SagaType.forTransactionKind(data.kind()), data, now);
        PendingCommand first = saga.start(now, policy);
        if (!sagas.insertIfAbsent(saga)) {
            log.info("Saga for transaction {} already exists; duplicate trigger ignored", data.transactionId());
            return Optional.empty();
        }
        commands.send(saga.id(), first);
        return Optional.of(saga.id());
    }

    @Transactional
    public void onReply(SagaReplyCommand reply) {
        Optional<SagaInstance> found = sagas.findForUpdate(reply.sagaId());
        if (found.isEmpty()) {
            log.warn("Reply for unknown saga {} (command {}) ignored", reply.sagaId(), reply.commandId());
            return;
        }
        SagaInstance saga = found.get();
        Optional<PendingCommand> next = saga.onReply(reply.commandId(), reply.success(), reply.errorCode(),
                reply.errorMessage(), clock.instant(), policy);
        persistAndSend(saga, next);
    }

    /** @return number of timed-out sagas handled */
    @Transactional
    public int processTimeouts(int batchSize) {
        Instant now = clock.instant();
        List<SagaInstance> due = sagas.lockDue(now, batchSize);
        for (SagaInstance saga : due) {
            persistAndSend(saga, saga.onTimeout(now, policy));
        }
        return due.size();
    }

    private void persistAndSend(SagaInstance saga, Optional<PendingCommand> next) {
        sagas.update(saga);
        next.ifPresent(command -> commands.send(saga.id(), command));
        if (saga.status() == SagaStatus.FAILED) {
            // Money may be inconsistent until someone intervenes: alert on this log line.
            log.error("SAGA FAILED - manual intervention required: saga={} type={} transaction={} code={} reason={}",
                    saga.id(), saga.type(), saga.data().transactionId(), saga.failureCode(), saga.failureReason());
        }
    }
}
