package com.budgetflow.orchestrator.adapter.in.messaging;

import static com.budgetflow.common.messaging.saga.CommandFields.optionalText;
import static com.budgetflow.common.messaging.saga.CommandFields.text;
import static com.budgetflow.common.messaging.saga.CommandFields.uuid;

import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.saga.SagaReply;
import com.budgetflow.orchestrator.application.SagaCoordinator;
import com.budgetflow.orchestrator.application.SagaReplyCommand;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Participant replies advance (or compensate) the saga they belong to. */
public class SagaReplyListener {

    public static final String QUEUE = "orchestrator-service.saga-replies";

    private final IdempotentConsumer inbox;
    private final SagaCoordinator coordinator;
    private final JsonMapper json;

    public SagaReplyListener(IdempotentConsumer inbox, SagaCoordinator coordinator, JsonMapper json) {
        this.inbox = inbox;
        this.coordinator = coordinator;
        this.json = json;
    }

    @RabbitListener(queues = QUEUE)
    public void onReply(Message message) {
        JsonNode r = json.readTree(message.getBody());
        SagaReplyCommand reply = new SagaReplyCommand(uuid(r, "sagaId"), uuid(r, "commandId"),
                SagaReply.Outcome.SUCCESS.name().equals(text(r, "outcome")), optionalText(r, "errorCode"),
                optionalText(r, "errorMessage"));
        inbox.handle(QueueTopology.messageId(message), "orchestrator.saga-replies", () -> coordinator.onReply(reply));
    }
}
