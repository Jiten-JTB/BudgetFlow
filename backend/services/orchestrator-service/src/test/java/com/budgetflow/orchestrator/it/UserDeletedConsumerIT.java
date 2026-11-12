package com.budgetflow.orchestrator.it;

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
class UserDeletedConsumerIT extends OrchestratorIntegrationTest {

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
            assertThat(count("saga_instance", userId)).as("saga_instance").isZero();
        });
        assertThat(count("saga_instance", other)).as("another user's data stays").isPositive();
    }

    private void seed(UUID userId) {
        jdbc.sql("""
                INSERT INTO saga_instance (id, saga_type, business_key, user_id, data, status, phase, step_index, created_at, updated_at, finished_at)
                VALUES (gen_random_uuid(), 'RECORD_TRANSACTION', gen_random_uuid()::text, :u, '{}', 'COMPLETED', 'DONE', 2, now(), now(), now())""").param("u", userId).update();
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
}
