package com.budgetflow.common.messaging.saga;

import java.util.UUID;

/** A participant's answer to one saga command. Contract v1 (additive changes only). */
public record SagaReply(UUID sagaId, UUID commandId, String commandType, Outcome outcome, String errorCode,
                        String errorMessage) {

    public enum Outcome { SUCCESS, FAILURE }

    public static SagaReply success(UUID sagaId, UUID commandId, String commandType) {
        return new SagaReply(sagaId, commandId, commandType, Outcome.SUCCESS, null, null);
    }

    public static SagaReply failure(UUID sagaId, UUID commandId, String commandType, String code, String message) {
        return new SagaReply(sagaId, commandId, commandType, Outcome.FAILURE, code, message);
    }
}
