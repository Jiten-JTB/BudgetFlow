package com.budgetflow.transaction.domain;

import static com.budgetflow.common.core.error.ErrorCategory.BUSINESS_RULE;
import static com.budgetflow.common.core.error.ErrorCategory.CONFLICT;
import static com.budgetflow.common.core.error.ErrorCategory.NOT_FOUND;
import static com.budgetflow.common.core.error.ErrorCategory.VALIDATION;

import com.budgetflow.common.core.error.ErrorCode;

public final class TransactionErrors {

    public static final ErrorCode TRANSACTION_NOT_FOUND = ErrorCode.of("TRANSACTION_NOT_FOUND", NOT_FOUND);
    public static final ErrorCode CATEGORY_NOT_FOUND = ErrorCode.of("CATEGORY_NOT_FOUND", NOT_FOUND);
    public static final ErrorCode INCOME_SOURCE_NOT_FOUND = ErrorCode.of("INCOME_SOURCE_NOT_FOUND", NOT_FOUND);

    public static final ErrorCode INVALID_TRANSACTION = ErrorCode.of("INVALID_TRANSACTION", VALIDATION);
    public static final ErrorCode INVALID_AMOUNT = ErrorCode.of("INVALID_AMOUNT", VALIDATION);
    public static final ErrorCode INVALID_CURRENCY = ErrorCode.of("INVALID_CURRENCY", VALIDATION);
    public static final ErrorCode INVALID_DATE = ErrorCode.of("INVALID_DATE", VALIDATION);
    public static final ErrorCode INVALID_KIND = ErrorCode.of("INVALID_KIND", VALIDATION);
    public static final ErrorCode INVALID_STATUS = ErrorCode.of("INVALID_STATUS", VALIDATION);
    public static final ErrorCode INVALID_TYPE = ErrorCode.of("INVALID_TYPE", VALIDATION);
    public static final ErrorCode INVALID_NAME = ErrorCode.of("INVALID_NAME", VALIDATION);
    public static final ErrorCode INVALID_COLOR = ErrorCode.of("INVALID_COLOR", VALIDATION);
    public static final ErrorCode INVALID_ICON = ErrorCode.of("INVALID_ICON", VALIDATION);
    public static final ErrorCode INVALID_PARENT_CATEGORY = ErrorCode.of("INVALID_PARENT_CATEGORY", VALIDATION);
    public static final ErrorCode CATEGORY_KIND_MISMATCH = ErrorCode.of("CATEGORY_KIND_MISMATCH", VALIDATION);
    public static final ErrorCode INVALID_CURSOR = ErrorCode.of("INVALID_CURSOR", VALIDATION);
    public static final ErrorCode IDEMPOTENCY_KEY_REQUIRED = ErrorCode.of("IDEMPOTENCY_KEY_REQUIRED", VALIDATION);

    public static final ErrorCode CATEGORY_ARCHIVED = ErrorCode.of("CATEGORY_ARCHIVED", BUSINESS_RULE);
    public static final ErrorCode INCOME_SOURCE_ARCHIVED = ErrorCode.of("INCOME_SOURCE_ARCHIVED", BUSINESS_RULE);
    public static final ErrorCode IDEMPOTENCY_KEY_REUSED = ErrorCode.of("IDEMPOTENCY_KEY_REUSED", BUSINESS_RULE);

    public static final ErrorCode DUPLICATE_NAME = ErrorCode.of("DUPLICATE_NAME", CONFLICT);
    public static final ErrorCode INVALID_STATE_TRANSITION = ErrorCode.of("INVALID_STATE_TRANSITION", CONFLICT);

    private TransactionErrors() {}
}
