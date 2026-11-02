package com.budgetflow.common.messaging.inbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a handler at most once per (message, consumer). The inbox record and the handler's
 * database work share one transaction, so a failed handler leaves the message re-processable.
 */
public class IdempotentConsumer {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public IdempotentConsumer(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** @return {@code true} if the handler ran, {@code false} if the message was a duplicate */
    public boolean handle(UUID messageId, String consumer, Runnable handler) {
        Boolean processed = tx.execute(status -> {
            int inserted = jdbc.sql("""
                    INSERT INTO inbox_message (message_id, consumer) VALUES (:messageId, :consumer)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("messageId", messageId)
                    .param("consumer", consumer)
                    .update();
            if (inserted == 0) {
                return false;
            }
            handler.run();
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
