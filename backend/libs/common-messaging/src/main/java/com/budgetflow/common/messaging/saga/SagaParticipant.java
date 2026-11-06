package com.budgetflow.common.messaging.saga;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs saga commands exactly once and replies through the outbox.
 * <ul>
 *   <li>Success: business effects, inbox record and SUCCESS reply commit together.</li>
 *   <li>{@link DomainException}: the business transaction rolls back entirely; a FAILURE reply carrying the
 *       error code is committed with the inbox record instead.</li>
 *   <li>Any other exception propagates, so the message is retried and finally dead-lettered; the
 *       orchestrator's step timeout then takes over.</li>
 * </ul>
 */
public final class SagaParticipant {

    private final IdempotentConsumer inbox;
    private final OutboxWriter outbox;
    private final JsonMapper json;
    private final String consumer;
    private final Map<String, CommandHandler> handlers;

    SagaParticipant(IdempotentConsumer inbox, OutboxWriter outbox, JsonMapper json, String serviceName,
                    Map<String, CommandHandler> handlers) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.json = json;
        this.consumer = serviceName + ".saga-commands";
        this.handlers = Map.copyOf(handlers);
    }

    public void handle(Message message) {
        UUID messageId = QueueTopology.messageId(message);
        String type = message.getMessageProperties().getType();
        CommandHandler handler = handlers.get(type);
        if (handler == null) {
            throw new AmqpRejectAndDontRequeueException("Unsupported saga command type: " + type);
        }
        JsonNode command = parse(message);
        UUID sagaId = requiredId(command, "sagaId");
        UUID commandId = requiredId(command, "commandId");
        try {
            inbox.handle(messageId, consumer, () -> {
                handler.handle(command);
                reply(SagaReply.success(sagaId, commandId, type));
            });
        } catch (DomainException e) {
            inbox.handle(messageId, consumer, () ->
                    reply(SagaReply.failure(sagaId, commandId, type, e.code().value(), e.getMessage())));
        }
    }

    private void reply(SagaReply reply) {
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.REPLIES)
                .routingKey(SagaContract.REPLY_ROUTING_KEY)
                .type(SagaContract.REPLY_TYPE)
                .aggregate("Saga", reply.sagaId().toString())
                .correlationId(reply.sagaId())
                .payload(reply)
                .build());
    }

    private JsonNode parse(Message message) {
        try {
            return json.readTree(message.getBody());
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("Saga command is not valid JSON", e);
        }
    }

    private static UUID requiredId(JsonNode command, String field) {
        try {
            return UUID.fromString(command.get(field).asString());
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("Saga command is missing a valid " + field, e);
        }
    }
}
