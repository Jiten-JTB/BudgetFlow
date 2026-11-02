package com.budgetflow.common.messaging.outbox;

import com.budgetflow.common.core.id.UuidV7;
import java.util.Objects;
import java.util.UUID;

/** A message to publish after the surrounding business transaction commits. */
public record OutboxMessage(
        UUID id,
        String exchange,
        String routingKey,
        String type,
        String aggregateType,
        String aggregateId,
        UUID correlationId,
        Object payload) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String exchange;
        private String routingKey;
        private String type;
        private String aggregateType;
        private String aggregateId;
        private UUID correlationId;
        private Object payload;

        private Builder() {}

        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder routingKey(String routingKey) {
            this.routingKey = routingKey;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder aggregate(String aggregateType, String aggregateId) {
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public OutboxMessage build() {
            return new OutboxMessage(
                    UuidV7.generate(), exchange, routingKey, type, aggregateType, aggregateId, correlationId, payload);
        }
    }
}
