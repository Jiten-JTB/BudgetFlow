package com.budgetflow.orchestrator.application.port;

import com.budgetflow.orchestrator.domain.PendingCommand;
import java.util.UUID;

/** Sends a saga command; implementations must enlist in the current transaction (outbox). */
public interface CommandBus {

    void send(UUID sagaId, PendingCommand command);
}
