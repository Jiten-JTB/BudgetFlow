package com.budgetflow.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.budgetflow.common.messaging.MessagingTestApplication;
import com.budgetflow.common.messaging.config.Exchanges;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** A consumer that keeps failing must not loop forever: bounded retries, then the dead-letter queue. */
@SpringBootTest(
        classes = MessagingTestApplication.class,
        properties = {
            "spring.rabbitmq.listener.simple.retry.max-retries=2",
            "spring.rabbitmq.listener.simple.retry.initial-interval=10ms"
        })
@Import(DeadLetterIT.FailingConsumer.class)
class DeadLetterIT {

    private static final String QUEUE = "dlq-it.failing";

    @Autowired RabbitTemplate rabbit;
    @Autowired FailingConsumer consumer;

    @Test
    void failingMessageIsRetriedThenDeadLettered() {
        rabbit.convertAndSend(Exchanges.EVENTS, "dlq-it.poison", "{\"x\":1}");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message dead = rabbit.receive(QUEUE + ".dlq");
            assertThat(dead).isNotNull();
        });
        assertThat(consumer.attempts).hasValue(3);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingConsumer {
        final AtomicInteger attempts = new AtomicInteger();

        @Bean
        Declarables failingTopology() {
            return QueueTopology.subscribe(QUEUE, Exchanges.EVENTS, "dlq-it.poison");
        }

        @RabbitListener(queues = QUEUE)
        void onMessage(Message message) {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }
    }
}
