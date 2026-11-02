package com.budgetflow.common.core.error;

import java.util.Map;
import java.util.Objects;

/** Base for all expected business failures. Unexpected failures stay as plain runtime exceptions. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> details;

    public DomainException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public DomainException(ErrorCode code, String message, Map<String, String> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }
}
