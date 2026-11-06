package com.budgetflow.orchestrator.adapter.out.messaging;

import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.common.messaging.saga.SagaContract;
import com.budgetflow.orchestrator.application.port.CommandBus;
import com.budgetflow.orchestrator.domain.PendingCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class OutboxCommandBus implements CommandBus {

    private final OutboxWriter outbox;

    public OutboxCommandBus(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void send(UUID sagaId, PendingCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sagaId", sagaId);
        body.put("commandId", command.commandId());
        body.putAll(command.fields());
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.COMMANDS)
                .routingKey(SagaContract.routingKeyFor(command.type()))
                .type(command.type())
                .aggregate("Saga", sagaId.toString())
                .correlationId(sagaId)
                .payload(body)
                .build());
    }
}
