package com.budgetflow.identity.domain;

import static com.budgetflow.common.core.error.ErrorCategory.CONFLICT;
import static com.budgetflow.common.core.error.ErrorCategory.NOT_FOUND;
import static com.budgetflow.common.core.error.ErrorCategory.VALIDATION;

import com.budgetflow.common.core.error.ErrorCode;

public final class IdentityErrors {

    public static final ErrorCode INVALID_EMAIL = ErrorCode.of("INVALID_EMAIL", VALIDATION);
    public static final ErrorCode WEAK_PASSWORD = ErrorCode.of("WEAK_PASSWORD", VALIDATION);
    public static final ErrorCode INVALID_DISPLAY_NAME = ErrorCode.of("INVALID_DISPLAY_NAME", VALIDATION);
    public static final ErrorCode INVALID_CURRENCY = ErrorCode.of("INVALID_CURRENCY", VALIDATION);
    public static final ErrorCode INVALID_TIMEZONE = ErrorCode.of("INVALID_TIMEZONE", VALIDATION);
    public static final ErrorCode EMAIL_ALREADY_REGISTERED = ErrorCode.of("EMAIL_ALREADY_REGISTERED", CONFLICT);
    public static final ErrorCode USER_NOT_FOUND = ErrorCode.of("USER_NOT_FOUND", NOT_FOUND);

    private IdentityErrors() {}
}
