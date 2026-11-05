package com.budgetflow.account.application;

import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.AccountType;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.common.core.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AccountService {

    public static final int MAX_PAGE_SIZE = 200;
    static final String DEFAULT_ACCOUNT_NAME = "Cash";

    private final AccountRepository accounts;
    private final BalanceEntryRepository entries;
    private final AccountEventPublisher events;
    private final LedgerWriter ledger;
    private final Clock clock;

    public AccountService(AccountRepository accounts, BalanceEntryRepository entries, AccountEventPublisher events,
                          Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.events = events;
        this.ledger = new LedgerWriter(accounts, entries, events);
        this.clock = clock;
    }

    @Transactional
    public Account open(UUID userId, OpenAccountCommand command) {
        AccountType type = Inputs.accountType(command.type());
        Currency currency = Inputs.currency(command.currency());
        Money opening = command.openingBalance() == null ? null : Inputs.money(command.openingBalance(), currency);
        Instant now = clock.instant();

        Account account = Account.open(UuidV7.generate(), userId, command.name(), type, currency, now);
        accounts.insert(account);
        events.accountOpened(account);
        if (opening != null && !opening.isZero()) {
            ledger.record(account, opening, EntryKind.OPENING, "open:" + account.id(), null, null, "Opening balance", now);
        }
        return account;
    }

    /** First account for a newly registered user. Idempotent: does nothing if the user has any account. */
    @Transactional
    public Optional<Account> openDefaultCashAccount(UUID userId, String currencyCode) {
        if (accounts.existsForUser(userId)) {
            return Optional.empty();
        }
        return Optional.of(open(userId, new OpenAccountCommand(DEFAULT_ACCOUNT_NAME, AccountType.CASH.name(),
                currencyCode, null)));
    }

    @Transactional(readOnly = true)
    public List<Account> list(UUID userId, boolean includeArchived) {
        return accounts.listByUser(userId, includeArchived);
    }

    @Transactional(readOnly = true)
    public Account get(UUID userId, UUID accountId) {
        return accounts.findOwned(userId, accountId).orElseThrow(AccountService::notFound);
    }

    @Transactional
    public Account rename(UUID userId, UUID accountId, String name) {
        Account account = get(userId, accountId);
        account.rename(name, clock.instant());
        accounts.update(account);
        return account;
    }

    @Transactional
    public Account archive(UUID userId, UUID accountId) {
        Account account = get(userId, accountId);
        account.archive(clock.instant());
        accounts.update(account);
        return account;
    }

    @Transactional(readOnly = true)
    public EntryPage entries(UUID userId, UUID accountId, UUID before, int limit) {
        get(userId, accountId); // ownership check
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        List<BalanceEntry> items = entries.page(accountId, before, size);
        return new EntryPage(items, items.size() == size ? items.getLast().id() : null);
    }

    static DomainException notFound() {
        return new DomainException(AccountErrors.ACCOUNT_NOT_FOUND, "Account not found");
    }
}
