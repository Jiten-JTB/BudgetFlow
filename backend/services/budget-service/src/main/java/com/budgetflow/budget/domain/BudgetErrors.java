package com.budgetflow.budget.domain;

import static com.budgetflow.common.core.error.ErrorCategory.BUSINESS_RULE;
import static com.budgetflow.common.core.error.ErrorCategory.CONFLICT;
import static com.budgetflow.common.core.error.ErrorCategory.NOT_FOUND;
import static com.budgetflow.common.core.error.ErrorCategory.VALIDATION;

import com.budgetflow.common.core.error.ErrorCode;

public final class BudgetErrors {

    public static final ErrorCode BUDGET_NOT_FOUND = ErrorCode.of("BUDGET_NOT_FOUND", NOT_FOUND);
    public static final ErrorCode BUDGET_ARCHIVED = ErrorCode.of("BUDGET_ARCHIVED", BUSINESS_RULE);
    public static final ErrorCode DUPLICATE_BUDGET = ErrorCode.of("DUPLICATE_BUDGET", CONFLICT);
    public static final ErrorCode OPERATION_CANCELLED = ErrorCode.of("OPERATION_CANCELLED", CONFLICT);
    public static final ErrorCode INVALID_NAME = ErrorCode.of("INVALID_NAME", VALIDATION);
    public static final ErrorCode INVALID_LIMIT = ErrorCode.of("INVALID_LIMIT", VALIDATION);
    public static final ErrorCode INVALID_THRESHOLD = ErrorCode.of("INVALID_THRESHOLD", VALIDATION);
    public static final ErrorCode INVALID_PERIOD = ErrorCode.of("INVALID_PERIOD", VALIDATION);
    public static final ErrorCode INVALID_CURRENCY = ErrorCode.of("INVALID_CURRENCY", VALIDATION);
    public static final ErrorCode INVALID_AMOUNT = ErrorCode.of("INVALID_AMOUNT", VALIDATION);
    public static final ErrorCode INVALID_DATE = ErrorCode.of("INVALID_DATE", VALIDATION);

    private BudgetErrors() {}
}
