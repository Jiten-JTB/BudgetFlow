package com.budgetflow.orchestrator.domain;

import java.time.Duration;

/**
 * @param stepTimeout how long to wait for a reply
 * @param maxAttempts sends of one command before giving up (retriable steps and compensations)
 */
public record SagaPolicy(Duration stepTimeout, int maxAttempts) {}
