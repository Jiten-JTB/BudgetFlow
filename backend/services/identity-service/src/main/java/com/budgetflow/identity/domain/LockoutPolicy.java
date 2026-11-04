package com.budgetflow.identity.domain;

import java.time.Duration;
import java.util.Objects;

public record LockoutPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LockoutPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts must be >= 1");
        }
        Objects.requireNonNull(lockDuration, "lockDuration");
    }
}
