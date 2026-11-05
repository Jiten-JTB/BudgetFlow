package com.budgetflow.account.application;

import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.AccountType;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.math.BigDecimal;
import java.util.Currency;

/** Parses untrusted request values into domain types with precise error codes. */
public final class Inputs {

    private Inputs() {}

    public static AccountType accountType(String raw) {
        try {
            return AccountType.valueOf(raw);
        } catch (RuntimeException e) {
            throw new DomainException(AccountErrors.INVALID_ACCOUNT_TYPE, "Unknown account type: " + raw);
        }
    }

    public static Currency currency(String code) {
        try {
            if (code != null && code.matches("[A-Z]{3}")) {
                return Currency.getInstance(code);
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw new DomainException(AccountErrors.INVALID_CURRENCY, "Unknown ISO 4217 currency: " + code);
    }

    public static Money money(String amount, Currency currency) {
        try {
            return Money.of(new BigDecimal(amount), currency);
        } catch (RuntimeException e) {
            throw new DomainException(AccountErrors.INVALID_AMOUNT, "Invalid amount: " + amount);
        }
    }
}
