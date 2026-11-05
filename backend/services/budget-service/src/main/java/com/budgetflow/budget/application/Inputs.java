package com.budgetflow.budget.application;

import com.budgetflow.budget.domain.BudgetErrors;
import com.budgetflow.budget.domain.BudgetPeriod;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;

/** Parses untrusted request values into domain types with precise error codes. */
public final class Inputs {

    private Inputs() {}

    public static BudgetPeriod period(String raw) {
        try {
            return BudgetPeriod.valueOf(raw);
        } catch (RuntimeException e) {
            throw new DomainException(BudgetErrors.INVALID_PERIOD, "Unknown period: " + raw);
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
        throw new DomainException(BudgetErrors.INVALID_CURRENCY, "Unknown ISO 4217 currency: " + code);
    }

    public static Money money(String amount, Currency currency) {
        try {
            return Money.of(new BigDecimal(amount), currency);
        } catch (RuntimeException e) {
            throw new DomainException(BudgetErrors.INVALID_AMOUNT, "Invalid amount: " + amount);
        }
    }

    public static LocalDate date(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new DomainException(BudgetErrors.INVALID_DATE, "Date must be yyyy-MM-dd: " + raw);
        }
    }
}
