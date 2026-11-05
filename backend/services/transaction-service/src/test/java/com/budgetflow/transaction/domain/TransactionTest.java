package com.budgetflow.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-04");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNT = UUID.randomUUID();

    @Test
    void newTransactionsArePendingWithSignedAmountByKind() {
        Transaction expense = request(TransactionKind.EXPENSE, null, UUID.randomUUID(), null, "250");
        Transaction income = request(TransactionKind.INCOME, null, null, UUID.randomUUID(), "1000");

        assertThat(expense.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(expense.signedAmount()).isEqualTo(Money.of("-250", "INR"));
        assertThat(income.signedAmount()).isEqualTo(Money.of("1000", "INR"));
    }

    @Test
    void enforcesShapePerKind() {
        assertError(() -> request(TransactionKind.EXPENSE, null, null, UUID.randomUUID(), "1"), TransactionErrors.INVALID_TRANSACTION);
        assertError(() -> request(TransactionKind.INCOME, OTHER_ACCOUNT, null, null, "1"), TransactionErrors.INVALID_TRANSACTION);
        assertError(() -> request(TransactionKind.TRANSFER, null, null, null, "1"), TransactionErrors.INVALID_TRANSACTION);
        assertError(() -> request(TransactionKind.TRANSFER, ACCOUNT, null, null, "1"), TransactionErrors.INVALID_TRANSACTION);
        assertError(() -> request(TransactionKind.TRANSFER, OTHER_ACCOUNT, UUID.randomUUID(), null, "1"), TransactionErrors.INVALID_TRANSACTION);
        assertThat(request(TransactionKind.TRANSFER, OTHER_ACCOUNT, null, null, "1").counterAccountId()).isEqualTo(OTHER_ACCOUNT);
    }

    @Test
    void amountMustBePositive() {
        assertError(() -> request(TransactionKind.EXPENSE, null, null, null, "0"), TransactionErrors.INVALID_AMOUNT);
        assertError(() -> request(TransactionKind.EXPENSE, null, null, null, "-5"), TransactionErrors.INVALID_AMOUNT);
    }

    @Test
    void dateMayBeAtMostOneDayAheadOfTodayToAllowForTimezones() {
        assertThat(requestOn(TODAY.plusDays(1)).occurredOn()).isEqualTo(TODAY.plusDays(1));
        assertError(() -> requestOn(TODAY.plusDays(2)), TransactionErrors.INVALID_DATE);
        assertError(() -> requestOn(LocalDate.of(1969, 12, 31)), TransactionErrors.INVALID_DATE);
    }

    @Test
    void completeAndCancelAreIdempotentAndMutuallyExclusive() {
        Transaction completed = request(TransactionKind.EXPENSE, null, null, null, "10");
        UUID saga = UUID.randomUUID();
        assertThat(completed.complete(saga, NOW)).isTrue();
        assertThat(completed.complete(saga, NOW)).isFalse();
        assertThat(completed.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(completed.sagaId()).isEqualTo(saga);
        assertThat(completed.completedAt()).isEqualTo(NOW);
        assertError(() -> completed.cancel("X", "y", NOW), TransactionErrors.INVALID_STATE_TRANSITION);

        Transaction cancelled = request(TransactionKind.EXPENSE, null, null, null, "10");
        assertThat(cancelled.cancel("ACCOUNT_ARCHIVED", "Account is archived", NOW)).isTrue();
        assertThat(cancelled.cancel("ACCOUNT_ARCHIVED", "Account is archived", NOW)).isFalse();
        assertThat(cancelled.failureCode()).isEqualTo("ACCOUNT_ARCHIVED");
        assertError(() -> cancelled.complete(saga, NOW), TransactionErrors.INVALID_STATE_TRANSITION);
    }

    @Test
    void trimsAndBoundsTextFields() {
        Transaction t = Transaction.request(UUID.randomUUID(), UUID.randomUUID(), TransactionKind.EXPENSE, ACCOUNT, null,
                Money.of("1", "INR"), null, null, TODAY, "  Lunch  ", "  ", "key-00000001", "h".repeat(64), TODAY, NOW);
        assertThat(t.description()).isEqualTo("Lunch");
        assertThat(t.payee()).isNull();
        assertError(() -> Transaction.request(UUID.randomUUID(), UUID.randomUUID(), TransactionKind.EXPENSE, ACCOUNT, null,
                Money.of("1", "INR"), null, null, TODAY, "x".repeat(201), null, "key-00000001", "h".repeat(64), TODAY, NOW),
                TransactionErrors.INVALID_TRANSACTION);
    }

    private static Transaction request(TransactionKind kind, UUID counter, UUID category, UUID source, String amount) {
        return Transaction.request(UUID.randomUUID(), UUID.randomUUID(), kind, ACCOUNT, counter, Money.of(amount, "INR"),
                category, source, TODAY, null, null, "key-00000001", "h".repeat(64), TODAY, NOW);
    }

    private static Transaction requestOn(LocalDate date) {
        return Transaction.request(UUID.randomUUID(), UUID.randomUUID(), TransactionKind.EXPENSE, ACCOUNT, null,
                Money.of("1", "INR"), null, null, date, null, null, "key-00000001", "h".repeat(64), TODAY, NOW);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
