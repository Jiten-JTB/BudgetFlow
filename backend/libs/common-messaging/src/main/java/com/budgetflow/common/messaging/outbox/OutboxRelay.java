package com.budgetflow.common.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox rows to RabbitMQ with publisher confirms.
 * {@code FOR UPDATE SKIP LOCKED} lets several service instances relay concurrently without duplicates.
 * A failing message stops the batch to preserve ordering; after {@code maxAttempts} it is parked.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx, int batchSize, int maxAttempts) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** @return number of messages published in this run */
    public int publishPending() {
        Integer published = tx.execute(status -> {
            List<PendingRow> rows = jdbc.sql("""
                    SELECT id, exchange, routing_key, message_type, correlation_id, payload::text AS payload
                    FROM outbox_message
                    WHERE published_at IS NULL AND attempts < :maxAttempts
                    ORDER BY id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """)
                    .param("maxAttempts", maxAttempts)
                    .param("batchSize", batchSize)
                    .query(PendingRow.class)
                    .list();
            int sent = 0;
            for (PendingRow row : rows) {
                try {
                    send(row);
                    jdbc.sql("UPDATE outbox_message SET published_at = clock_timestamp(), attempts = attempts + 1 WHERE id = :id")
                            .param("id", row.id())
                            .update();
                    sent++;
                } catch (RuntimeException e) {
                    log.warn("Outbox publish failed for message {} ({})", row.id(), row.messageType(), e);
                    jdbc.sql("UPDATE outbox_message SET attempts = attempts + 1, last_error = :error WHERE id = :id")
                            .param("id", row.id())
                            .param("error", truncate(String.valueOf(e)))
                            .update();
                    break;
                }
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    private void send(PendingRow row) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(row.id().toString());
        props.setType(row.messageType());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (row.correlationId() != null) {
            props.setCorrelationId(row.correlationId().toString());
        }
        Message message = new Message(row.payload().getBytes(StandardCharsets.UTF_8), props);
        rabbit.invoke(ops -> {
            ops.send(row.exchange(), row.routingKey(), message);
            ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    record PendingRow(UUID id, String exchange, String routingKey, String messageType, UUID correlationId, String payload) {}
}
