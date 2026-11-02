package com.budgetflow.common.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    private static final UUID CORRELATION = UUID.randomUUID();

    @Test
    void buildsMessageWithGeneratedTimeOrderedId() {
        OutboxMessage message = OutboxMessage.builder()
                .exchange("budgetflow.events")
                .routingKey("account.balance-changed")
                .type("account.BalanceChanged")
                .aggregate("Account", "acc-1")
                .correlationId(CORRELATION)
                .payload(new Sample("x", 1))
                .build();

        assertThat(message.id().version()).isEqualTo(7);
        assertThat(message.correlationId()).isEqualTo(CORRELATION);
        assertThat(message.aggregateType()).isEqualTo("Account");
        assertThat(message.aggregateId()).isEqualTo("acc-1");
        assertThat(message.payload()).isEqualTo(new Sample("x", 1));
    }

    @Test
    void requiresRoutingInformationTypeAndPayload() {
        assertThatThrownBy(() -> OutboxMessage.builder().routingKey("r").type("t").payload("p").build())
                .isInstanceOf(NullPointerException.class).hasMessageContaining("exchange");
        assertThatThrownBy(() -> OutboxMessage.builder().exchange("e").type("t").payload("p").build())
                .isInstanceOf(NullPointerException.class).hasMessageContaining("routingKey");
        assertThatThrownBy(() -> OutboxMessage.builder().exchange("e").routingKey("r").payload("p").build())
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> OutboxMessage.builder().exchange("e").routingKey("r").type("t").build())
                .isInstanceOf(NullPointerException.class).hasMessageContaining("payload");
    }

    record Sample(String name, int value) {}
}
