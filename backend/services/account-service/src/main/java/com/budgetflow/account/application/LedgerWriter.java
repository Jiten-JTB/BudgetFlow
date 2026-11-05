package com.budgetflow.account.application;

import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.util.UUID;

/** The only code path that changes a balance: account row, ledger line and event, in one transaction. */
final class LedgerWriter {

    private final AccountRepository accounts;
    private final BalanceEntryRepository entries;
    private final AccountEventPublisher events;

    LedgerWriter(AccountRepository accounts, BalanceEntryRepository entries, AccountEventPublisher events) {
        this.accounts = accounts;
        this.entries = entries;
        this.events = events;
    }

    BalanceEntry record(Account account, Money amount, EntryKind kind, String operationKey, UUID referenceId,
                        UUID reversesEntryId, String description, Instant now) {
        Money balanceAfter = account.applyDelta(amount, kind == EntryKind.REVERSAL, now);
        accounts.update(account);
        BalanceEntry entry = new BalanceEntry(UuidV7.generate(), account.id(), account.userId(), operationKey, kind,
                amount, balanceAfter, referenceId, reversesEntryId, description, now);
        entries.insert(entry);
        events.balanceChanged(account, entry);
        return entry;
    }
}
