package com.budgetflow.orchestrator.application;

import java.util.UUID;

public record SagaReplyCommand(UUID sagaId, UUID commandId, boolean success, String errorCode, String errorMessage) {}
