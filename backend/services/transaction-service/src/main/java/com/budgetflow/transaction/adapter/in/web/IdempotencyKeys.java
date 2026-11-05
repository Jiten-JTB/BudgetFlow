package com.budgetflow.transaction.adapter.in.web;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.util.regex.Pattern;

final class IdempotencyKeys {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private IdempotencyKeys() {}

    static String require(String key) {
        if (key == null || !FORMAT.matcher(key).matches()) {
            throw new DomainException(TransactionErrors.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header (8-100 chars of A-Z a-z 0-9 _ -) is required");
        }
        return key;
    }
}
