package com.budgetflow.common.core.error;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, machine-readable error identifier (UPPER_SNAKE_CASE) shared with clients. */
public record ErrorCode(String value, ErrorCategory category) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    public ErrorCode {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(category, "category");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Error code must be UPPER_SNAKE_CASE: " + value);
        }
    }

    public static ErrorCode of(String value, ErrorCategory category) {
        return new ErrorCode(value, category);
    }
}
