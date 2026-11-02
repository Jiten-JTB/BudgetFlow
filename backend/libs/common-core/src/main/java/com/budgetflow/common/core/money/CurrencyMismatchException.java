package com.budgetflow.common.core.money;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCategory;
import com.budgetflow.common.core.error.ErrorCode;
import java.util.Currency;
import java.util.Map;

public final class CurrencyMismatchException extends DomainException {

    public static final ErrorCode CODE = ErrorCode.of("CURRENCY_MISMATCH", ErrorCategory.BUSINESS_RULE);

    public CurrencyMismatchException(Currency left, Currency right) {
        super(CODE,
                "Currency mismatch: " + left.getCurrencyCode() + " vs " + right.getCurrencyCode(),
                Map.of("left", left.getCurrencyCode(), "right", right.getCurrencyCode()));
    }
}
