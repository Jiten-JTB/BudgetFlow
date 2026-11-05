package com.budgetflow.account.domain;

import static com.budgetflow.common.core.error.ErrorCategory.BUSINESS_RULE;
import static com.budgetflow.common.core.error.ErrorCategory.CONFLICT;
import static com.budgetflow.common.core.error.ErrorCategory.NOT_FOUND;
import static com.budgetflow.common.core.error.ErrorCategory.VALIDATION;

import com.budgetflow.common.core.error.ErrorCode;

public final class AccountErrors {

    public static final ErrorCode ACCOUNT_NOT_FOUND = ErrorCode.of("ACCOUNT_NOT_FOUND", NOT_FOUND);
    public static final ErrorCode ACCOUNT_ARCHIVED = ErrorCode.of("ACCOUNT_ARCHIVED", BUSINESS_RULE);
    public static final ErrorCode ACCOUNT_CURRENCY_MISMATCH = ErrorCode.of("ACCOUNT_CURRENCY_MISMATCH", BUSINESS_RULE);
    public static final ErrorCode AMOUNT_OUT_OF_RANGE = ErrorCode.of("AMOUNT_OUT_OF_RANGE", BUSINESS_RULE);
    public static final ErrorCode DUPLICATE_ACCOUNT_NAME = ErrorCode.of("DUPLICATE_ACCOUNT_NAME", CONFLICT);
    public static final ErrorCode OPERATION_CANCELLED = ErrorCode.of("OPERATION_CANCELLED", CONFLICT);
    public static final ErrorCode INVALID_ACCOUNT_NAME = ErrorCode.of("INVALID_ACCOUNT_NAME", VALIDATION);
    public static final ErrorCode INVALID_ACCOUNT_TYPE = ErrorCode.of("INVALID_ACCOUNT_TYPE", VALIDATION);
    public static final ErrorCode INVALID_CURRENCY = ErrorCode.of("INVALID_CURRENCY", VALIDATION);
    public static final ErrorCode INVALID_AMOUNT = ErrorCode.of("INVALID_AMOUNT", VALIDATION);
    public static final ErrorCode IDEMPOTENCY_KEY_REQUIRED = ErrorCode.of("IDEMPOTENCY_KEY_REQUIRED", VALIDATION);

    private AccountErrors() {}
}
