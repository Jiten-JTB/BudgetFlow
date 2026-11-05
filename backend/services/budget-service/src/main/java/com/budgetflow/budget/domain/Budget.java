package com.budgetflow.budget.domain;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** A soft spending limit for one category (or all expenses when {@code categoryId} is null) per calendar period. */
public final class Budget {

    private static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final UUID userId;
    private String name;
    private final UUID categoryId;
    private final BudgetPeriod period;
    private Money limit;
    private int alertThresholdPercent;
    private final LocalDate startsOn;
    private Instant archivedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Budget(UUID id, UUID userId, String name, UUID categoryId, BudgetPeriod period, Money limit,
                   int alertThresholdPercent, LocalDate startsOn, Instant archivedAt, Instant createdAt,
                   Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.name = name;
        this.categoryId = categoryId;
        this.period = Objects.requireNonNull(period);
        this.limit = Objects.requireNonNull(limit);
        this.alertThresholdPercent = alertThresholdPercent;
        this.startsOn = Objects.requireNonNull(startsOn);
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Budget create(UUID id, UUID userId, String name, UUID categoryId, BudgetPeriod period, Money limit,
                                int alertThresholdPercent, LocalDate startsOn, Instant now) {
        return new Budget(id, userId, validName(name), categoryId, period, validLimit(limit),
                validThreshold(alertThresholdPercent), startsOn, null, now, now, 0);
    }

    public static Budget rehydrate(UUID id, UUID userId, String name, UUID categoryId, BudgetPeriod period, Money limit,
                                   int alertThresholdPercent, LocalDate startsOn, Instant archivedAt,
                                   Instant createdAt, Instant updatedAt, long version) {
        return new Budget(id, userId, name, categoryId, period, limit, alertThresholdPercent, startsOn, archivedAt,
                createdAt, updatedAt, version);
    }

    /** {@code null} arguments leave the field unchanged. The currency of a budget never changes. */
    public void update(String newName, Money newLimit, Integer newThreshold, Instant now) {
        if (isArchived()) {
            throw new DomainException(BudgetErrors.BUDGET_ARCHIVED, "Budget is archived");
        }
        if (newLimit != null && !newLimit.currency().equals(limit.currency())) {
            throw new DomainException(BudgetErrors.INVALID_LIMIT, "Limit must stay in " + limit.currency().getCurrencyCode());
        }
        if (newName != null) {
            name = validName(newName);
        }
        if (newLimit != null) {
            limit = validLimit(newLimit);
        }
        if (newThreshold != null) {
            alertThresholdPercent = validThreshold(newThreshold);
        }
        updatedAt = now;
    }

    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
            updatedAt = now;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean appliesTo(LocalDate date, Currency currency) {
        return !isArchived() && limit.currency().equals(currency) && !date.isBefore(startsOn);
    }

    public Currency currency() {
        return limit.currency();
    }

    private static String validName(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(BudgetErrors.INVALID_NAME, "Budget name must be 1-" + MAX_NAME_LENGTH + " printable characters");
        }
        return value;
    }

    private static Money validLimit(Money limit) {
        if (!limit.isPositive()) {
            throw new DomainException(BudgetErrors.INVALID_LIMIT, "Limit must be greater than zero");
        }
        return limit;
    }

    private static int validThreshold(int percent) {
        if (percent < 1 || percent > 100) {
            throw new DomainException(BudgetErrors.INVALID_THRESHOLD, "Alert threshold must be 1-100 percent");
        }
        return percent;
    }

    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String name() { return name; }
    public UUID categoryId() { return categoryId; }
    public BudgetPeriod period() { return period; }
    public Money limit() { return limit; }
    public int alertThresholdPercent() { return alertThresholdPercent; }
    public LocalDate startsOn() { return startsOn; }
    public Instant archivedAt() { return archivedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
