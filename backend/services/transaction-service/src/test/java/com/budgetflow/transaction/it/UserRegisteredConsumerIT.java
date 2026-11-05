package com.budgetflow.transaction.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.budgetflow.common.messaging.config.Exchanges;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class UserRegisteredConsumerIT extends TransactionIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void seedsDefaultCategoriesExactlyOnceEvenIfRedelivered() {
        UUID userId = UUID.randomUUID();
        Message event = userRegistered(UUID.randomUUID(), userId);

        rabbit.send(Exchanges.EVENTS, "identity.user-registered", event);
        rabbit.send(Exchanges.EVENTS, "identity.user-registered", event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long income = count(userId, "INCOME");
            long expense = count(userId, "EXPENSE");
            assertThat(income).isEqualTo(6);
            assertThat(expense).isEqualTo(12);
        });
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(count(userId, "EXPENSE")).isEqualTo(12));
    }

    private long count(UUID userId, String kind) {
        return jdbc.sql("SELECT count(*) FROM category WHERE user_id = :u AND kind = :k")
                .param("u", userId).param("k", kind).query(Long.class).single();
    }

    private static Message userRegistered(UUID messageId, UUID userId) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId.toString());
        props.setType("identity.UserRegistered");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        String body = """
                {"userId":"%s","email":"u@example.com","displayName":"U","baseCurrency":"EUR",
                 "timezone":"Europe/Berlin","registeredAt":"2026-11-04T10:00:00Z"}""".formatted(userId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
