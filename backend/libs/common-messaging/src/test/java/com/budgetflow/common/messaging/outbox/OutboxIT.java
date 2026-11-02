package com.budgetflow.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.messaging.MessagingTestApplication;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = MessagingTestApplication.class)
class OutboxIT {

    private static final String EXCHANGE = "budgetflow.events";
    private static final String QUEUE = "outbox-it";

    @Autowired OutboxWriter writer;
    @Autowired OutboxRelay relay;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM outbox_message").update();
        admin.declareQueue(new Queue(QUEUE));
        admin.declareBinding(BindingBuilder.bind(new Queue(QUEUE)).to(new TopicExchange(EXCHANGE)).with("test.#"));
        admin.purgeQueue(QUEUE);
    }

    @Test
    void appendRequiresAnActiveTransaction() {
        assertThatThrownBy(() -> writer.append(message("test.a")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void appendIsDiscardedWhenBusinessTransactionRollsBack() {
        tx.executeWithoutResult(status -> {
            writer.append(message("test.a"));
            status.setRollbackOnly();
        });

        assertThat(pendingCount()).isZero();
    }

    @Test
    void relayPublishesPendingMessagesWithHeadersAndMarksThemPublished() {
        UUID correlation = UUID.randomUUID();
        OutboxMessage outbox = OutboxMessage.builder()
                .exchange(EXCHANGE).routingKey("test.created").type("test.Created")
                .aggregate("Thing", "t-1").correlationId(correlation)
                .payload(Map.of("name", "salary"))
                .build();
        tx.executeWithoutResult(s -> writer.append(outbox));

        int published = relay.publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(pendingCount()).isZero();
        Message received = rabbit.receive(QUEUE, 5_000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(outbox.id().toString());
        assertThat(received.getMessageProperties().getCorrelationId()).isEqualTo(correlation.toString());
        assertThat(received.getMessageProperties().getType()).isEqualTo("test.Created");
        assertThat(received.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(new String(received.getBody())).isEqualTo("{\"name\":\"salary\"}");
    }

    @Test
    void relayPublishesInCreationOrder() {
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 5; i++) {
                writer.append(OutboxMessage.builder()
                        .exchange(EXCHANGE).routingKey("test.seq").type("test.Seq").payload(Map.of("n", i)).build());
            }
        });

        relay.publishPending();

        for (int i = 0; i < 5; i++) {
            assertThat(new String(rabbit.receive(QUEUE, 5_000).getBody())).isEqualTo("{\"n\":" + i + "}");
        }
    }

    @Test
    void failedPublishIsRetriedUpToMaxAttemptsAndRecordsError() {
        tx.executeWithoutResult(s -> writer.append(OutboxMessage.builder()
                .exchange("does-not-exist").routingKey("x").type("test.X").payload(Map.of()).build()));

        for (int i = 0; i < 5; i++) {
            relay.publishPending();
        }

        var row = jdbc.sql("SELECT attempts, last_error, published_at FROM outbox_message").query().singleRow();
        assertThat(row.get("attempts")).isEqualTo(3);
        assertThat((String) row.get("last_error")).isNotBlank();
        assertThat(row.get("published_at")).isNull();
    }

    private long pendingCount() {
        return jdbc.sql("SELECT count(*) FROM outbox_message WHERE published_at IS NULL").query(Long.class).single();
    }

    private static OutboxMessage message(String routingKey) {
        return OutboxMessage.builder()
                .exchange(EXCHANGE).routingKey(routingKey).type("test.Event").payload(Map.of("k", "v")).build();
    }
}
