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

/** identity.UserDeleted erases this user's data and nobody else's. */
class UserDeletedConsumerIT extends TransactionIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void erasesEverythingKeptForTheDeletedUserOnly() {
        UUID userId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        seed(userId);
        seed(other);

        rabbit.send(Exchanges.EVENTS, "identity.user-deleted", userDeleted(userId));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("category", userId)).as("category").isZero();
            assertThat(count("income_source", userId)).as("income_source").isZero();
            assertThat(count("money_transaction", userId)).as("money_transaction").isZero();
        });
        assertThat(count("category", other)).as("another user's data stays").isPositive();
    }

    private void seed(UUID userId) {
        rabbit.send(Exchanges.EVENTS, "identity.user-registered", userRegistered(userId));
        await().atMost(Duration.ofSeconds(10)).until(() -> count("category", userId) > 0);
    }

    private int count(String table, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :u").param("u", userId).query(Integer.class).single();
    }

    private static Message userDeleted(UUID userId) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setType("identity.UserDeleted");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        String body = "{\"userId\":\"%s\",\"deletedAt\":\"2026-11-10T10:00:00Z\"}".formatted(userId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    private static Message userRegistered(UUID userId) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setType("identity.UserRegistered");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        String body = """
                {"userId":"%s","email":"u@example.com","displayName":"U","baseCurrency":"INR",
                 "timezone":"Asia/Kolkata","registeredAt":"2026-11-04T10:00:00Z"}""".formatted(userId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
