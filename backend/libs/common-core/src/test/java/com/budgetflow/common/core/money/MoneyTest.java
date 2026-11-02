package com.budgetflow.common.core.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalisesScaleToFourDecimalPlaces() {
        Money money = Money.of("10.5", "INR");

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.5000"));
        assertThat(money.currency()).isEqualTo(Currency.getInstance("INR"));
    }

    @Test
    void roundsHalfEvenBeyondFourDecimalPlaces() {
        assertThat(Money.of("1.00005", "USD").amount()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(Money.of("1.00015", "USD").amount()).isEqualTo(new BigDecimal("1.0002"));
    }

    @Test
    void equalityIgnoresInputScale() {
        assertThat(Money.of("10", "EUR")).isEqualTo(Money.of("10.0000", "EUR"));
        assertThat(Money.of("10", "EUR")).hasSameHashCodeAs(Money.of("10.00", "EUR"));
    }

    @Test
    void addsAndSubtractsSameCurrency() {
        Money a = Money.of("100.25", "INR");
        Money b = Money.of("0.75", "INR");

        assertThat(a.plus(b)).isEqualTo(Money.of("101", "INR"));
        assertThat(a.minus(b)).isEqualTo(Money.of("99.5", "INR"));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
        Money inr = Money.of("1", "INR");
        Money usd = Money.of("1", "USD");

        assertThatThrownBy(() -> inr.plus(usd))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("INR")
                .hasMessageContaining("USD");
        assertThatThrownBy(() -> inr.minus(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> inr.isGreaterThan(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void negatesAndReportsSign() {
        Money positive = Money.of("5", "INR");

        assertThat(positive.negate()).isEqualTo(Money.of("-5", "INR"));
        assertThat(positive.isPositive()).isTrue();
        assertThat(positive.negate().isNegative()).isTrue();
        assertThat(Money.zero("INR").isZero()).isTrue();
    }

    @Test
    void comparesSameCurrency() {
        assertThat(Money.of("10", "INR").isGreaterThan(Money.of("9.9999", "INR"))).isTrue();
        assertThat(Money.of("10", "INR").isGreaterThan(Money.of("10", "INR"))).isFalse();
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, Currency.getInstance("INR")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of("1", "XXXX")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("abc", "INR")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void rejectsAmountsOutsideNumeric19_4() {
        assertThatThrownBy(() -> Money.of("1000000000000000", "INR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
        assertThat(Money.of("999999999999999.9999", "INR").amount())
                .isEqualTo(new BigDecimal("999999999999999.9999"));
    }

    @Test
    void toStringIsPlainAmountAndCurrency() {
        assertThat(Money.of("1E+3", "INR")).hasToString("1000.0000 INR");
    }
}
