package com.budgetflow.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.budgetflow.common.messaging.MessagingTestApplication;
import com.budgetflow.common.messaging.config.Exchanges;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = MessagingTestApplication.class,
        properties = {
            "budgetflow.messaging.outbox.relay-enabled=true",
            "budgetflow.messaging.outbox.poll-interval=100ms"
        })
class OutboxRelaySchedulerIT {

    private static final String QUEUE = "outbox-scheduler-it";

    @Autowired OutboxWriter writer;
    @Autowired TransactionTemplate tx;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;

    @Test
    void backgroundRelayPublishesCommittedMessagesWithoutManualTrigger() {
        admin.declareQueue(new Queue(QUEUE));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE))
                .to(new TopicExchange(Exchanges.EVENTS)).with("scheduler.#"));

        tx.executeWithoutResult(s -> writer.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS).routingKey("scheduler.tick").type("test.Tick")
                .payload(Map.of("ok", true)).build()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(rabbit.receive(QUEUE)).isNotNull());
    }
}
