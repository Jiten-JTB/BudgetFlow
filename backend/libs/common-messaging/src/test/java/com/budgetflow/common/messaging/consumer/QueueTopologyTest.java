package com.budgetflow.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.common.messaging.config.Exchanges;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class QueueTopologyTest {

    @Test
    void declaresDurableQueueWithDeadLetterQueueAndBindings() {
        var declarables = QueueTopology.subscribe("txn.user-registered", Exchanges.EVENTS, "identity.user-registered");

        Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals("txn.user-registered")).findFirst().orElseThrow();
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", Exchanges.DEAD_LETTER)
                .containsEntry("x-dead-letter-routing-key", "txn.user-registered")
                .containsEntry("x-queue-type", "quorum");

        Queue dlq = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals("txn.user-registered.dlq")).findFirst().orElseThrow();
        assertThat(dlq.isDurable()).isTrue();

        assertThat(declarables.getDeclarablesByType(DirectExchange.class))
                .extracting(DirectExchange::getName).containsExactly(Exchanges.DEAD_LETTER);
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .extracting(b -> b.getExchange() + "|" + b.getRoutingKey() + "->" + b.getDestination())
                .containsExactlyInAnyOrder(
                        Exchanges.EVENTS + "|identity.user-registered->txn.user-registered",
                        Exchanges.DEAD_LETTER + "|txn.user-registered->txn.user-registered.dlq");
    }

    @Test
    void readsMessageIdFromAmqpProperties() {
        var props = new org.springframework.amqp.core.MessageProperties();
        java.util.UUID id = java.util.UUID.randomUUID();
        props.setMessageId(id.toString());

        assertThat(QueueTopology.messageId(new org.springframework.amqp.core.Message(new byte[0], props))).isEqualTo(id);
    }

    @Test
    void rejectsMessagesWithoutUsableIdWithoutRequeue() {
        var message = new org.springframework.amqp.core.Message(new byte[0], new org.springframework.amqp.core.MessageProperties());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> QueueTopology.messageId(message))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    }
}
