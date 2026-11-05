package com.budgetflow.common.messaging.consumer;

import com.budgetflow.common.messaging.config.Exchanges;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/** Consumer-side conventions: one durable quorum queue per subscriber, each with its own dead-letter queue. */
public final class QueueTopology {

    private QueueTopology() {}

    /**
     * @param queue      owned by the consuming service, e.g. {@code transaction-service.user-registered}
     * @param exchange   topic exchange to subscribe to
     * @param routingKey routing key / pattern
     */
    public static Declarables subscribe(String queue, String exchange, String routingKey) {
        Queue main = QueueBuilder.durable(queue)
                .quorum()
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(queue)
                .build();
        Queue deadLetters = QueueBuilder.durable(queue + ".dlq").quorum().build();
        DirectExchange dlx = new DirectExchange(Exchanges.DEAD_LETTER);
        Binding subscription = BindingBuilder.bind(main).to(new TopicExchange(exchange)).with(routingKey);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetters).to(dlx).with(queue);
        return new Declarables(main, deadLetters, dlx, subscription, deadLetterBinding);
    }

    /** The publisher's message id (outbox id); required for idempotent consumption. */
    public static UUID messageId(Message message) {
        String id = message.getMessageProperties().getMessageId();
        try {
            return UUID.fromString(id);
        } catch (RuntimeException e) {
            // Unprocessable without an id: retrying cannot help, dead-letter it.
            throw new AmqpRejectAndDontRequeueException("Message has no valid message id: " + id, e);
        }
    }
}
