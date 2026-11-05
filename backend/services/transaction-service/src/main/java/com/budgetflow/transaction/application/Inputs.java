package com.budgetflow.transaction.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.common.core.money.Money;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;

/** Parses untrusted request values into domain types with precise error codes. */
public final class Inputs {

    private Inputs() {}

    public static <E extends Enum<E>> E enumValue(Class<E> type, String raw, ErrorCode error) {
        try {
            return Enum.valueOf(type, raw);
        } catch (RuntimeException e) {
            throw new DomainException(error, "Unknown value: " + raw);
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
        throw new DomainException(TransactionErrors.INVALID_CURRENCY, "Unknown ISO 4217 currency: " + code);
    }

    public static Money money(String amount, Currency currency) {
        try {
            return Money.of(new BigDecimal(amount), currency);
        } catch (RuntimeException e) {
            throw new DomainException(TransactionErrors.INVALID_AMOUNT, "Invalid amount: " + amount);
        }
    }

    /** ISO-8601 calendar date (yyyy-MM-dd); null stays null. */
    public static LocalDate date(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new DomainException(TransactionErrors.INVALID_DATE, "Date must be yyyy-MM-dd: " + raw);
        }
    }
}
