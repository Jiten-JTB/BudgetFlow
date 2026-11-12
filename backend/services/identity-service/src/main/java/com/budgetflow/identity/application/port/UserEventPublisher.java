package com.budgetflow.identity.application.port;

import com.budgetflow.identity.domain.User;
import java.time.Instant;
import java.util.UUID;

/** Publishes integration events; must enlist in the current transaction (outbox). */
public interface UserEventPublisher {

    void userRegistered(User user);

    void userDeleted(UUID userId, Instant deletedAt);
}
