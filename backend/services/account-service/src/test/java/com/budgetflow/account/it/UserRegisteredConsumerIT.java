package com.budgetflow.account.it;

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

class UserRegisteredConsumerIT extends AccountIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;

    @Test
    void createsDefaultCashAccountInBaseCurrencyExactlyOnceEvenIfRedelivered() {
        UUID userId = UUID.randomUUID();
        Message event = userRegistered(UUID.randomUUID(), userId);

        rabbit.send(Exchanges.EVENTS, "identity.user-registered", event);
        rabbit.send(Exchanges.EVENTS, "identity.user-registered", event); // redelivery, same message id

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT count(*) FROM inbox_message WHERE consumer = 'account.user-registered'"
                        + " AND message_id = :m").param("m", UUID.fromString(event.getMessageProperties().getMessageId()))
                        .query(Long.class).single()).isEqualTo(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var rows = jdbc.sql("SELECT name, type, currency FROM account WHERE user_id = :u")
                    .param("u", userId).query().listOfRows();
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst()).containsEntry("name", "Cash").containsEntry("type", "CASH").containsEntry("currency", "EUR");
        });
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
