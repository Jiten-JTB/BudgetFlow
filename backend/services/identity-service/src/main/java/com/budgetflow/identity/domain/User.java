package com.budgetflow.identity.domain;

import com.budgetflow.common.core.error.DomainException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** User aggregate: identity, profile preferences and brute-force lockout state. */
public final class User {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UUID id;
    private final Email email;
    private final String passwordHash;
    private String displayName;
    private Currency baseCurrency;
    private ZoneId timezone;
    private final UserStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private User(UUID id, Email email, String passwordHash, String displayName, Currency baseCurrency, ZoneId timezone,
                 UserStatus status, int failedLoginAttempts, Instant lockedUntil, Instant lastLoginAt,
                 Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = displayName;
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency");
        this.timezone = Objects.requireNonNull(timezone, "timezone");
        this.status = Objects.requireNonNull(status, "status");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static User register(UUID id, Email email, String passwordHash, String displayName,
                                Currency baseCurrency, ZoneId timezone, Instant now) {
        return new User(id, email, passwordHash, validDisplayName(displayName), baseCurrency, timezone,
                UserStatus.ACTIVE, 0, null, null, now, now, 0);
    }

    /** Rebuilds a persisted user; no validation beyond nulls, the database is trusted. */
    public static User rehydrate(UUID id, Email email, String passwordHash, String displayName, Currency baseCurrency,
                                 ZoneId timezone, UserStatus status, int failedLoginAttempts, Instant lockedUntil,
                                 Instant lastLoginAt, Instant createdAt, Instant updatedAt, long version) {
        return new User(id, email, passwordHash, displayName, baseCurrency, timezone, status, failedLoginAttempts,
                lockedUntil, lastLoginAt, createdAt, updatedAt, version);
    }

    public static String validDisplayName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > MAX_DISPLAY_NAME_LENGTH || name.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(IdentityErrors.INVALID_DISPLAY_NAME,
                    "Display name must be 1-" + MAX_DISPLAY_NAME_LENGTH + " printable characters");
        }
        return name;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** @return {@code true} if this failure locked the account */
    public boolean recordFailedLogin(Instant now, LockoutPolicy policy) {
        if (lockedUntil != null && !isLockedAt(now)) {
            // Previous lock has expired: start a fresh counting window.
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        updatedAt = now;
        if (failedLoginAttempts >= policy.maxFailedAttempts() && lockedUntil == null) {
            lockedUntil = now.plus(policy.lockDuration());
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    /** {@code null} arguments leave the field unchanged. */
    public void updateProfile(String newDisplayName, Currency newBaseCurrency, ZoneId newTimezone, Instant now) {
        if (newDisplayName != null) {
            displayName = validDisplayName(newDisplayName);
        }
        if (newBaseCurrency != null) {
            baseCurrency = newBaseCurrency;
        }
        if (newTimezone != null) {
            timezone = newTimezone;
        }
        updatedAt = now;
    }

    /** Persistence hook: called after a successful optimistic-locked write. */
    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public Email email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String displayName() { return displayName; }
    public Currency baseCurrency() { return baseCurrency; }
    public ZoneId timezone() { return timezone; }
    public UserStatus status() { return status; }
    public int failedLoginAttempts() { return failedLoginAttempts; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
