package com.budgetflow.orchestrator.domain;

import java.util.Map;
import java.util.UUID;

/** A command issued by a saga; the participant's reply must quote {@code commandId}. */
public record PendingCommand(UUID commandId, String type, Map<String, Object> fields) {}
