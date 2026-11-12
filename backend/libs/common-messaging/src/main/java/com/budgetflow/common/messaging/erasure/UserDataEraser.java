package com.budgetflow.common.messaging.erasure;

import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Erases everything one service holds about a user when identity publishes {@code identity.UserDeleted}.
 * Each service lists its own DELETE statements (children before parents, each using {@code :userId}); they run in one
 * transaction with the inbox record, so a redelivery is a no-op and a failure leaves the event to be retried.
 * Already-published outbox messages that mention the user are removed too; unpublished ones are left to deliver.
 */
public class UserDataEraser {

    public static final String ROUTING_KEY = "identity.user-deleted";
    private static final Pattern USER_ID = Pattern.compile("\"userId\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    private final String queue;
    private final String consumer;
    private final IdempotentConsumer inbox;
    private final JdbcClient jdbc;
    private final List<String> statements;

    public UserDataEraser(String queue, String consumer, IdempotentConsumer inbox, JdbcClient jdbc, List<String> statements) {
        this.queue = queue;
        this.consumer = consumer;
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.statements = List.copyOf(statements);
    }

    /** The service's own queue for UserDeleted, with its dead-letter queue, bound to the events exchange. */
    public static Declarables topology(String queue) {
        return QueueTopology.subscribe(queue, Exchanges.EVENTS, ROUTING_KEY);
    }

    public String queue() {
        return queue;
    }

    /**
     * A container for this service's UserDeleted queue, built by the shared listener factory so it acknowledges,
     * rejects and dead-letters exactly like every other listener. Register it as a bean; it starts with the context.
     */
    public SimpleMessageListenerContainer container(SimpleRabbitListenerContainerFactory factory) {
        SimpleMessageListenerContainer container = factory.createListenerContainer();
        container.setQueueNames(queue);
        container.setMessageListener(this::onMessage);
        return container;
    }

    public void onMessage(Message message) {
        UUID messageId = QueueTopology.messageId(message);
        UUID userId = userId(message);
        inbox.handle(messageId, consumer, () -> {
            statements.forEach(sql -> jdbc.sql(sql).param("userId", userId).update());
            jdbc.sql("""
                    DELETE FROM outbox_message
                    WHERE published_at IS NOT NULL AND payload::text LIKE '%' || :userId || '%'""")
                    .param("userId", userId.toString()).update();
        });
    }

    private static UUID userId(Message message) {
        Matcher m = USER_ID.matcher(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        if (!m.find()) {
            throw new IllegalArgumentException("UserDeleted event without a userId");
        }
        return UUID.fromString(m.group(1));
    }
}
