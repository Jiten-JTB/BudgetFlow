package com.budgetflow.identity.adapter.out.messaging;

import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.identity.application.port.UserEventPublisher;
import com.budgetflow.identity.domain.User;
import java.time.Instant;
import java.util.UUID;

public class OutboxUserEventPublisher implements UserEventPublisher {

    private final OutboxWriter outbox;

    public OutboxUserEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void userRegistered(User user) {
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS)
                .routingKey("identity.user-registered")
                .type("identity.UserRegistered")
                .aggregate("User", user.id().toString())
                .payload(new UserRegistered(user.id(), user.email().value(), user.displayName(),
                        user.baseCurrency().getCurrencyCode(), user.timezone().getId(), user.createdAt()))
                .build());
    }

    @Override
    public void userDeleted(UUID userId, Instant deletedAt) {
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS)
                .routingKey("identity.user-deleted")
                .type("identity.UserDeleted")
                .aggregate("User", userId.toString())
                .payload(new UserDeleted(userId, deletedAt))
                .build());
    }

    /** Integration event contract v1: every service erases all data it keeps for this user. */
    record UserDeleted(UUID userId, Instant deletedAt) {}

    /** Integration event contract v1. Additive changes only. */
    record UserRegistered(UUID userId, String email, String displayName, String baseCurrency, String timezone,
                          Instant registeredAt) {}
}
