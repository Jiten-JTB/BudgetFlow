package com.budgetflow.common.messaging.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Stores outgoing messages in the caller's transaction, so state change and message commit atomically. */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public OutboxWriter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void append(OutboxMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("OutboxWriter.append requires an active transaction");
        }
        jdbc.sql("""
                INSERT INTO outbox_message
                    (id, exchange, routing_key, message_type, aggregate_type, aggregate_id, correlation_id, payload)
                VALUES (:id, :exchange, :routingKey, :type, :aggregateType, :aggregateId, :correlationId,
                        CAST(:payload AS JSON))
                """)
                .param("id", message.id())
                .param("exchange", message.exchange())
                .param("routingKey", message.routingKey())
                .param("type", message.type())
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("correlationId", message.correlationId())
                .param("payload", json.writeValueAsString(message.payload()))
                .update();
    }
}
