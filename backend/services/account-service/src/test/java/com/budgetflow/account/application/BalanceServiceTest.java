package com.budgetflow.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.account.application.port.OperationRegistry;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.AccountType;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BalanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BalanceEntryRepository entries = mock(BalanceEntryRepository.class);
    private final OperationRegistry operations = mock(OperationRegistry.class);
    private final AccountEventPublisher events = mock(AccountEventPublisher.class);
    private final BalanceService service =
            new BalanceService(accounts, entries, operations, events, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Account account =
            Account.open(UUID.randomUUID(), USER, "Cash", AccountType.CASH, Currency.getInstance("INR"), NOW);

    @Test
    void appliesChangeUnderOperationLockAndRecordsLedgerEntry() {
        when(accounts.findOwnedForUpdate(USER, account.id())).thenReturn(Optional.of(account));

        BalanceChange change = service.apply(command("txn:1:apply", "-40"));

        assertThat(change.duplicate()).isFalse();
        assertThat(change.entry().amount()).isEqualTo(Money.of("-40", "INR"));
        assertThat(change.entry().balanceAfter()).isEqualTo(Money.of("-40", "INR"));
        verify(operations).lock("txn:1:apply");
        verify(accounts).update(account);
        verify(entries).insert(change.entry());
        verify(events).balanceChanged(account, change.entry());
    }

    @Test
    void replayedOperationReturnsOriginalResultWithoutChangingBalance() {
        BalanceEntry original = entry("txn:1:apply", "-40");
        when(entries.findByOperationKey("txn:1:apply")).thenReturn(Optional.of(original));

        BalanceChange change = service.apply(command("txn:1:apply", "-40"));

        assertThat(change.duplicate()).isTrue();
        assertThat(change.entry()).isEqualTo(original);
        verify(accounts, never()).update(any());
        verify(entries, never()).insert(any());
    }

    @Test
    void refusesOperationThatWasAlreadyCompensated() {
        when(operations.isCancelled("txn:1:apply")).thenReturn(true);

        assertThatThrownBy(() -> service.apply(command("txn:1:apply", "-40")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(AccountErrors.OPERATION_CANCELLED);
    }

    @Test
    void reversingUnknownOperationLeavesTombstone() {
        Reversal reversal = service.reverse(USER, "txn:9:apply");

        assertThat(reversal.outcome()).isEqualTo(Reversal.Outcome.NOTHING_TO_REVERSE);
        verify(operations).lock("txn:9:apply");
        verify(operations).cancel("txn:9:apply", USER, NOW);
    }

    @Test
    void reversesAppliedOperationEvenOnArchivedAccount() {
        BalanceEntry original = entry("txn:1:apply", "-40");
        account.applyDelta(Money.of("-40", "INR"), false, NOW);
        account.archive(NOW);
        when(entries.findByOperationKey("txn:1:apply")).thenReturn(Optional.of(original));
        when(accounts.findOwnedForUpdate(USER, account.id())).thenReturn(Optional.of(account));

        Reversal reversal = service.reverse(USER, "txn:1:apply");

        assertThat(reversal.outcome()).isEqualTo(Reversal.Outcome.REVERSED);
        ArgumentCaptor<BalanceEntry> inserted = ArgumentCaptor.forClass(BalanceEntry.class);
        verify(entries).insert(inserted.capture());
        assertThat(inserted.getValue().kind()).isEqualTo(EntryKind.REVERSAL);
        assertThat(inserted.getValue().amount()).isEqualTo(Money.of("40", "INR"));
        assertThat(inserted.getValue().reversesEntryId()).isEqualTo(original.id());
        assertThat(inserted.getValue().operationKey()).isEqualTo("txn:1:apply:reversal");
        assertThat(account.balance()).isEqualTo(Money.zero("INR"));
    }

    @Test
    void secondReversalIsIdempotent() {
        BalanceEntry original = entry("txn:1:apply", "-40");
        BalanceEntry existingReversal = entry("txn:1:apply:reversal", "40");
        when(entries.findByOperationKey("txn:1:apply")).thenReturn(Optional.of(original));
        when(entries.findReversalOf(original.id())).thenReturn(Optional.of(existingReversal));

        Reversal reversal = service.reverse(USER, "txn:1:apply");

        assertThat(reversal.outcome()).isEqualTo(Reversal.Outcome.ALREADY_REVERSED);
        assertThat(reversal.entry()).isEqualTo(existingReversal);
        verify(entries, never()).insert(any());
    }

    @Test
    void onlyTransactionTransferAndAdjustmentKindsCanBeAppliedDirectly() {
        assertThatThrownBy(() -> service.apply(new ApplyBalanceChangeCommand(USER, account.id(),
                Money.of("1", "INR"), EntryKind.REVERSAL, "k", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ApplyBalanceChangeCommand command(String key, String amount) {
        return new ApplyBalanceChangeCommand(USER, account.id(), Money.of(amount, "INR"), EntryKind.TRANSACTION,
                key, UUID.randomUUID(), "Groceries");
    }

    private BalanceEntry entry(String key, String amount) {
        return new BalanceEntry(UUID.randomUUID(), account.id(), USER, key, EntryKind.TRANSACTION,
                Money.of(amount, "INR"), Money.of(amount, "INR"), null, null, null, NOW);
    }
}
