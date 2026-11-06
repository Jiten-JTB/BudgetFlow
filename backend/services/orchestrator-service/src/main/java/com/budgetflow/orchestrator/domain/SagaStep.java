package com.budgetflow.orchestrator.domain;

/**
 * @param compensation undoes {@code forward}; null when there is nothing to undo
 * @param retriable    resend on timeout instead of compensating (steps after the money has moved)
 */
public record SagaStep(String name, SagaCommand forward, SagaCommand compensation, boolean retriable) {}
