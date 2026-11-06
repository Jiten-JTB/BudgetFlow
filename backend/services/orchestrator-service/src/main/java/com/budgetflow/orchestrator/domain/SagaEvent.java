package com.budgetflow.orchestrator.domain;

import java.time.Instant;
import java.util.UUID;

public record SagaEvent(SagaEventType type, String commandType, UUID commandId, String detail, Instant occurredAt) {}
