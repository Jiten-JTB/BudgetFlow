package com.budgetflow.common.core.error;

/** Transport-neutral error classes; adapters map them to HTTP statuses or saga failure reasons. */
public enum ErrorCategory {
    VALIDATION(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    BUSINESS_RULE(422),
    INTERNAL(500);

    private final int httpStatus;

    ErrorCategory(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
