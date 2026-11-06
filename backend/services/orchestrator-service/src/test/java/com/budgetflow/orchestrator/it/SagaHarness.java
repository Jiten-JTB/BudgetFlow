package com.budgetflow.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Plays the saga participants: captures every command the orchestrator sends and answers it. */
final class SagaHarness {

    private final RabbitTemplate rabbit;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String queue;

    SagaHarness(RabbitTemplate rabbit, AmqpAdmin admin, String name) {
        this.rabbit = rabbit;
        this.queue = "orchestrator-it." + name;
        admin.declareQueue(new Queue(queue));
        admin.declareBinding(BindingBuilder.bind(new Queue(queue)).to(new TopicExchange("budgetflow.commands")).with("#"));
        admin.purgeQueue(queue);
    }

    record Command(String type, UUID sagaId, UUID commandId, JsonNode body) {}

    void publishTransactionRequested(UUID transactionId, UUID userId, String kind, UUID accountId, UUID counterAccountId) {
        String body = """
                {"transactionId":"%s","userId":"%s","kind":"%s","status":"PENDING","accountId":"%s",
                 "counterAccountId":%s,"amount":"850.0000","currency":"INR","categoryId":null,"parentCategoryId":null,
                 "incomeSourceId":null,"occurredOn":"2026-11-06","failureCode":null,"occurredAt":"2026-11-06T10:00:00Z"}"""
                .formatted(transactionId, userId, kind, accountId,
                        counterAccountId == null ? "null" : "\"" + counterAccountId + "\"");
        rabbit.send("budgetflow.events", "transaction.transaction-requested", message("transaction.TransactionRequested", body));
    }

    Command nextCommand() {
        Message m = rabbit.receive(queue, 10_000);
        assertThat(m).as("expected a saga command").isNotNull();
        JsonNode body = json.readTree(m.getBody());
        return new Command(m.getMessageProperties().getType(), UUID.fromString(body.get("sagaId").asString()),
                UUID.fromString(body.get("commandId").asString()), body);
    }

    void assertNoCommand(long waitMillis) {
        assertThat(rabbit.receive(queue, waitMillis)).as("no further command").isNull();
    }

    void succeed(Command c) {
        reply(c, "SUCCESS", null, null);
    }

    void fail(Command c, String code, String message) {
        reply(c, "FAILURE", code, message);
    }

    void reply(Command c, String outcome, String code, String message) {
        String body = """
                {"sagaId":"%s","commandId":"%s","commandType":"%s","outcome":"%s","errorCode":%s,"errorMessage":%s}"""
                .formatted(c.sagaId(), c.commandId(), c.type(), outcome, quote(code), quote(message));
        rabbit.send("budgetflow.replies", "orchestrator.saga-reply", message("saga.Reply", body));
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static Message message(String type, String body) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setType(type);
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
