package com.budgetflow.common.messaging.saga;

import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.JsonNode;

/**
 * Reads saga command fields. A missing or malformed required field is a contract violation (a bug, not a
 * business outcome): the message is dead-lettered and the orchestrator's step timeout compensates.
 */
public final class CommandFields {

    private CommandFields() {}

    public static String text(JsonNode command, String field) {
        String value = optionalText(command, field);
        if (value == null) {
            throw new AmqpRejectAndDontRequeueException("Saga command is missing '" + field + "'");
        }
        return value;
    }

    public static String optionalText(JsonNode command, String field) {
        JsonNode value = command.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public static UUID uuid(JsonNode command, String field) {
        try {
            return UUID.fromString(text(command, field));
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Saga command field '" + field + "' is not a UUID", e);
        }
    }

    public static UUID optionalUuid(JsonNode command, String field) {
        return optionalText(command, field) == null ? null : uuid(command, field);
    }
}
