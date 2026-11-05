package com.budgetflow.account.application;

import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.account.application.port.OperationRegistry;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga participant operations on balances. Every operation is idempotent by key, serialised per key,
 * and compensation is safe in any order relative to the operation it undoes.
 */
public class BalanceService {

    private static final Set<EntryKind> DIRECT_KINDS = EnumSet.of(EntryKind.TRANSACTION, EntryKind.TRANSFER, EntryKind.ADJUSTMENT);
    private static final String REVERSAL_SUFFIX = ":reversal";

    private final AccountRepository accounts;
    private final BalanceEntryRepository entries;
    private final OperationRegistry operations;
    private final LedgerWriter ledger;
    private final Clock clock;

    public BalanceService(AccountRepository accounts, BalanceEntryRepository entries, OperationRegistry operations,
                          AccountEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.operations = operations;
        this.ledger = new LedgerWriter(accounts, entries, events);
        this.clock = clock;
    }

    @Transactional
    public BalanceChange apply(ApplyBalanceChangeCommand command) {
        if (!DIRECT_KINDS.contains(command.kind())) {
            throw new IllegalArgumentException("Kind " + command.kind() + " cannot be applied directly");
        }
        operations.lock(command.operationKey());
        Optional<BalanceEntry> existing = entries.findByOperationKey(command.operationKey());
        if (existing.isPresent()) {
            if (!existing.get().userId().equals(command.userId())) {
                throw AccountService.notFound();
            }
            return new BalanceChange(existing.get(), true);
        }
        if (operations.isCancelled(command.operationKey())) {
            throw new DomainException(AccountErrors.OPERATION_CANCELLED,
                    "Operation " + command.operationKey() + " was cancelled");
        }
        Account account = accounts.findOwnedForUpdate(command.userId(), command.accountId())
                .orElseThrow(AccountService::notFound);
        BalanceEntry entry = ledger.record(account, command.amount(), command.kind(), command.operationKey(),
                command.referenceId(), null, command.description(), clock.instant());
        return new BalanceChange(entry, false);
    }

    @Transactional
    public Reversal reverse(UUID userId, String operationKey) {
        operations.lock(operationKey);
        Instant now = clock.instant();
        Optional<BalanceEntry> original = entries.findByOperationKey(operationKey)
                .filter(entry -> entry.userId().equals(userId));
        if (original.isEmpty()) {
            if (!operations.isCancelled(operationKey)) {
                operations.cancel(operationKey, userId, now);
            }
            return new Reversal(Reversal.Outcome.NOTHING_TO_REVERSE, null);
        }
        Optional<BalanceEntry> previous = entries.findReversalOf(original.get().id());
        if (previous.isPresent()) {
            return new Reversal(Reversal.Outcome.ALREADY_REVERSED, previous.get());
        }
        BalanceEntry entry = original.get();
        Account account = accounts.findOwnedForUpdate(userId, entry.accountId()).orElseThrow(AccountService::notFound);
        BalanceEntry reversal = ledger.record(account, entry.amount().negate(), EntryKind.REVERSAL,
                operationKey + REVERSAL_SUFFIX, entry.referenceId(), entry.id(), "Reversal: " + operationKey, now);
        return new Reversal(Reversal.Outcome.REVERSED, reversal);
    }

    /** Manual balance correction by the user; the client's Idempotency-Key makes retries safe. */
    @Transactional
    public BalanceChange adjust(UUID userId, UUID accountId, String amount, String idempotencyKey, String description) {
        Account account = accounts.findOwned(userId, accountId).orElseThrow(AccountService::notFound);
        Money delta = Inputs.money(amount, account.currency());
        return apply(new ApplyBalanceChangeCommand(userId, accountId, delta, EntryKind.ADJUSTMENT,
                "adj:" + accountId + ":" + idempotencyKey, null, description));
    }
}
