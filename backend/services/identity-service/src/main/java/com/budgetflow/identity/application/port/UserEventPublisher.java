package com.budgetflow.identity.application.port;

import com.budgetflow.identity.domain.User;

/** Publishes integration events; must enlist in the current transaction (outbox). */
public interface UserEventPublisher {

    void userRegistered(User user);
}
