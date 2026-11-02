package com.budgetflow.common.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.messaging.MessagingTestApplication;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(classes = MessagingTestApplication.class)
class IdempotentConsumerIT {

    @Autowired IdempotentConsumer consumer;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM inbox_message").update();
    }

    @Test
    void processesEachMessageOncePerConsumer() {
        UUID messageId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        boolean first = consumer.handle(messageId, "budget.consume", calls::incrementAndGet);
        boolean duplicate = consumer.handle(messageId, "budget.consume", calls::incrementAndGet);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test
    void differentConsumersProcessTheSameMessageIndependently() {
        UUID messageId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        consumer.handle(messageId, "reporting.projector", calls::incrementAndGet);
        consumer.handle(messageId, "notification.alerts", calls::incrementAndGet);

        assertThat(calls).hasValue(2);
    }

    @Test
    void failedHandlingIsNotRecordedSoRedeliveryIsProcessed() {
        UUID messageId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> consumer.handle(messageId, "c", () -> {
            jdbc.sql("INSERT INTO inbox_message(message_id, consumer) VALUES (gen_random_uuid(), 'side-effect')").update();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.sql("SELECT count(*) FROM inbox_message").query(Long.class).single())
                .as("inbox record and handler side effects roll back together").isZero();
        assertThat(consumer.handle(messageId, "c", calls::incrementAndGet)).isTrue();
        assertThat(calls).hasValue(1);
    }
}
