package com.budgetflow.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Currency INR = Currency.getInstance("INR");

    @Test
    void opensActiveAccountWithZeroBalanceAndTrimmedName() {
        Account account = open();

        assertThat(account.name()).isEqualTo("HDFC Savings");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.zero("INR"));
    }

    @Test
    void rejectsInvalidNames() {
        for (String bad : new String[] {null, "  ", "x".repeat(101), "tab\tname"}) {
            assertError(() -> Account.open(UUID.randomUUID(), UUID.randomUUID(), bad, AccountType.BANK, INR, NOW),
                    AccountErrors.INVALID_ACCOUNT_NAME);
        }
    }

    @Test
    void appliesSignedDeltasAllowingNegativeBalances() {
        Account account = open();

        assertThat(account.applyDelta(Money.of("100", "INR"), false, NOW)).isEqualTo(Money.of("100", "INR"));
        assertThat(account.applyDelta(Money.of("-250.50", "INR"), false, NOW)).isEqualTo(Money.of("-150.50", "INR"));
    }

    @Test
    void rejectsZeroDeltaAndForeignCurrency() {
        Account account = open();

        assertError(() -> account.applyDelta(Money.zero("INR"), false, NOW), AccountErrors.INVALID_AMOUNT);
        assertError(() -> account.applyDelta(Money.of("1", "USD"), false, NOW), AccountErrors.ACCOUNT_CURRENCY_MISMATCH);
    }

    @Test
    void archivedAccountRejectsNewMoneyButAcceptsCompensation() {
        Account account = open();
        account.applyDelta(Money.of("100", "INR"), false, NOW);
        account.archive(NOW);
        account.archive(NOW); // idempotent

        assertError(() -> account.applyDelta(Money.of("1", "INR"), false, NOW), AccountErrors.ACCOUNT_ARCHIVED);
        assertThat(account.applyDelta(Money.of("-100", "INR"), true, NOW)).isEqualTo(Money.zero("INR"));
    }

    @Test
    void renameValidatesAndRejectsArchivedAccounts() {
        Account account = open();
        account.rename(" Salary Account ", NOW);
        assertThat(account.name()).isEqualTo("Salary Account");

        account.archive(NOW);
        assertError(() -> account.rename("New", NOW), AccountErrors.ACCOUNT_ARCHIVED);
    }

    private static Account open() {
        return Account.open(UUID.randomUUID(), UUID.randomUUID(), "  HDFC Savings ", AccountType.BANK, INR, NOW);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
