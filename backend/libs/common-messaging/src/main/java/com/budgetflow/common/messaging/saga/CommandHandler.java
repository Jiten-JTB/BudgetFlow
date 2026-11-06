package com.budgetflow.common.messaging.saga;

import tools.jackson.databind.JsonNode;

/** Executes one saga command. Throw a DomainException for an expected business refusal. */
@FunctionalInterface
public interface CommandHandler {

    void handle(JsonNode command);
}
