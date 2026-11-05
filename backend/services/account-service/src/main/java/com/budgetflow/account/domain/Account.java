package com.budgetflow.account.domain;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * A place money lives. Negative balances are allowed for every type: Budget Flow records what
 * happened (overdrafts, card debt) rather than refusing it.
 */
public final class Account {

    private static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final UUID userId;
    private String name;
    private final AccountType type;
    private final Currency currency;
    private Money balance;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Account(UUID id, UUID userId, String name, AccountType type, Currency currency, Money balance,
                    AccountStatus status, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.name = name;
        this.type = Objects.requireNonNull(type);
        this.currency = Objects.requireNonNull(currency);
        this.balance = Objects.requireNonNull(balance);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static Account open(UUID id, UUID userId, String name, AccountType type, Currency currency, Instant now) {
        return new Account(id, userId, validName(name), type, currency, Money.of(java.math.BigDecimal.ZERO, currency),
                AccountStatus.ACTIVE, now, now, 0);
    }

    public static Account rehydrate(UUID id, UUID userId, String name, AccountType type, Currency currency,
                                    Money balance, AccountStatus status, Instant createdAt, Instant updatedAt,
                                    long version) {
        return new Account(id, userId, name, type, currency, balance, status, createdAt, updatedAt, version);
    }

    public void rename(String newName, Instant now) {
        requireActive();
        name = validName(newName);
        updatedAt = now;
    }

    /** Idempotent. History stays; the account just stops accepting new money. */
    public void archive(Instant now) {
        if (status != AccountStatus.ARCHIVED) {
            status = AccountStatus.ARCHIVED;
            updatedAt = now;
        }
    }

    /**
     * @param compensation reversals must always succeed, even on archived accounts
     * @return the new balance
     */
    public Money applyDelta(Money delta, boolean compensation, Instant now) {
        if (!delta.currency().equals(currency)) {
            throw new DomainException(AccountErrors.ACCOUNT_CURRENCY_MISMATCH,
                    "Account currency is " + currency.getCurrencyCode() + ", not " + delta.currency().getCurrencyCode());
        }
        if (delta.isZero()) {
            throw new DomainException(AccountErrors.INVALID_AMOUNT, "Amount must not be zero");
        }
        if (!compensation) {
            requireActive();
        }
        try {
            balance = balance.plus(delta);
        } catch (IllegalArgumentException e) {
            throw new DomainException(AccountErrors.AMOUNT_OUT_OF_RANGE, "Resulting balance is out of range");
        }
        updatedAt = now;
        return balance;
    }

    public void incrementVersion() {
        version++;
    }

    private void requireActive() {
        if (status == AccountStatus.ARCHIVED) {
            throw new DomainException(AccountErrors.ACCOUNT_ARCHIVED, "Account is archived");
        }
    }

    private static String validName(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(AccountErrors.INVALID_ACCOUNT_NAME,
                    "Account name must be 1-" + MAX_NAME_LENGTH + " printable characters");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String name() { return name; }
    public AccountType type() { return type; }
    public Currency currency() { return currency; }
    public Money balance() { return balance; }
    public AccountStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
