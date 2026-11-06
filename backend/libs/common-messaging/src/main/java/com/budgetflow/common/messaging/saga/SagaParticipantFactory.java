package com.budgetflow.common.messaging.saga;

import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public class SagaParticipantFactory {

    private final IdempotentConsumer inbox;
    private final OutboxWriter outbox;
    private final JsonMapper json;

    public SagaParticipantFactory(IdempotentConsumer inbox, OutboxWriter outbox, JsonMapper json) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.json = json;
    }

    /** @param handlers command type (e.g. {@code account.ApplyBalanceChange}) to handler */
    public SagaParticipant create(String serviceName, Map<String, CommandHandler> handlers) {
        return new SagaParticipant(inbox, outbox, json, serviceName, handlers);
    }
}
