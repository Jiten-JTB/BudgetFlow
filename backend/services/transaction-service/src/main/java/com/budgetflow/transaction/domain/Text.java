package com.budgetflow.transaction.domain;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;

/** Shared rules for user-entered text. */
final class Text {

    private Text() {}

    static String required(String raw, int max, ErrorCode error, String field) {
        String value = optional(raw, max, error, field);
        if (value == null) {
            throw new DomainException(error, field + " is required");
        }
        return value;
    }

    /** Trimmed; blank becomes null; control characters are rejected. */
    static String optional(String raw, int max, ErrorCode error, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(error, field + " must be at most " + max + " printable characters");
        }
        return value;
    }
}
