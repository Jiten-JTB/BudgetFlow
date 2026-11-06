package com.budgetflow.common.test;

import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Drives a saga participant over RabbitMQ the way the orchestrator does, and reads its reply from the outbox. */
public final class SagaCommands {

    public static final String COMMANDS_EXCHANGE = "budgetflow.commands";

    private SagaCommands() {}

    /**
     * Publishes a command and waits for the participant's reply.
     *
     * @param fields command fields; values are written as JSON strings (null allowed)
     * @return the reply payload JSON
     */
    public static String sendAndAwaitReply(RabbitTemplate rabbit, JdbcClient jdbc, String type, String routingKey,
                                           Map<String, ?> fields) {
        UUID sagaId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sagaId", sagaId);
        body.put("commandId", commandId);
        body.putAll(fields);
        String json = body.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() == null ? "null" : "\"" + e.getValue() + "\""))
                .collect(Collectors.joining(",", "{", "}"));

        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setType(type);
        props.setCorrelationId(sagaId.toString());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(COMMANDS_EXCHANGE, routingKey, new Message(json.getBytes(StandardCharsets.UTF_8), props));

        return await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.sql("""
                        SELECT payload::text FROM outbox_message
                        WHERE message_type = 'saga.Reply' AND aggregate_id = :sagaId""")
                .param("sagaId", sagaId.toString()).query(String.class).optional().orElse(null),
                java.util.Objects::nonNull);
    }
}
