package com.budgetflow.transaction.domain;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A money movement as the user recorded it. Lifecycle: PENDING -> COMPLETED | CANCELLED, driven by the
 * RecordTransaction saga. The account balance is owned by account-service, never by this aggregate.
 */
public final class Transaction {

    private static final LocalDate EARLIEST = LocalDate.of(1970, 1, 1);
    private static final int MAX_FUTURE_DAYS = 1; // the user's "today" may be ahead of UTC

    private final UUID id;
    private final UUID userId;
    private final TransactionKind kind;
    private TransactionStatus status;
    private final UUID accountId;
    private final UUID counterAccountId;
    private final Money amount;
    private final UUID categoryId;
    private final UUID incomeSourceId;
    private final LocalDate occurredOn;
    private final String description;
    private final String payee;
    private final String idempotencyKey;
    private final String requestHash;
    private UUID sagaId;
    private String failureCode;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private long version;

    private Transaction(UUID id, UUID userId, TransactionKind kind, TransactionStatus status, UUID accountId,
                        UUID counterAccountId, Money amount, UUID categoryId, UUID incomeSourceId, LocalDate occurredOn,
                        String description, String payee, String idempotencyKey, String requestHash, UUID sagaId,
                        String failureCode, String failureReason, Instant createdAt, Instant updatedAt,
                        Instant completedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.kind = Objects.requireNonNull(kind);
        this.status = Objects.requireNonNull(status);
        this.accountId = Objects.requireNonNull(accountId);
        this.counterAccountId = counterAccountId;
        this.amount = Objects.requireNonNull(amount);
        this.categoryId = categoryId;
        this.incomeSourceId = incomeSourceId;
        this.occurredOn = Objects.requireNonNull(occurredOn);
        this.description = description;
        this.payee = payee;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestHash = Objects.requireNonNull(requestHash);
        this.sagaId = sagaId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.completedAt = completedAt;
        this.version = version;
    }

    public static Transaction request(UUID id, UUID userId, TransactionKind kind, UUID accountId, UUID counterAccountId,
                                      Money amount, UUID categoryId, UUID incomeSourceId, LocalDate occurredOn,
                                      String description, String payee, String idempotencyKey, String requestHash,
                                      LocalDate today, Instant now) {
        if (!amount.isPositive()) {
            throw new DomainException(TransactionErrors.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        validateShape(kind, accountId, counterAccountId, categoryId, incomeSourceId);
        if (occurredOn.isBefore(EARLIEST) || occurredOn.isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw new DomainException(TransactionErrors.INVALID_DATE, "Date must be between " + EARLIEST + " and tomorrow");
        }
        return new Transaction(id, userId, kind, TransactionStatus.PENDING, accountId, counterAccountId, amount,
                categoryId, incomeSourceId, occurredOn,
                Text.optional(description, 200, TransactionErrors.INVALID_TRANSACTION, "Description"),
                Text.optional(payee, 100, TransactionErrors.INVALID_TRANSACTION, "Payee"),
                idempotencyKey, requestHash, null, null, null, now, now, null, 0);
    }

    public static Transaction rehydrate(UUID id, UUID userId, TransactionKind kind, TransactionStatus status,
                                        UUID accountId, UUID counterAccountId, Money amount, UUID categoryId,
                                        UUID incomeSourceId, LocalDate occurredOn, String description, String payee,
                                        String idempotencyKey, String requestHash, UUID sagaId, String failureCode,
                                        String failureReason, Instant createdAt, Instant updatedAt,
                                        Instant completedAt, long version) {
        return new Transaction(id, userId, kind, status, accountId, counterAccountId, amount, categoryId,
                incomeSourceId, occurredOn, description, payee, idempotencyKey, requestHash, sagaId, failureCode,
                failureReason, createdAt, updatedAt, completedAt, version);
    }

    private static void validateShape(TransactionKind kind, UUID accountId, UUID counterAccountId, UUID categoryId,
                                      UUID incomeSourceId) {
        String problem = switch (kind) {
            case INCOME -> counterAccountId != null ? "Income cannot have a counter account" : null;
            case EXPENSE -> counterAccountId != null ? "Expense cannot have a counter account"
                    : incomeSourceId != null ? "Expense cannot have an income source" : null;
            case TRANSFER -> counterAccountId == null ? "Transfer requires a counter account"
                    : counterAccountId.equals(accountId) ? "Transfer accounts must differ"
                    : categoryId != null || incomeSourceId != null ? "Transfer cannot have a category or income source"
                    : null;
        };
        if (problem != null) {
            throw new DomainException(TransactionErrors.INVALID_TRANSACTION, problem);
        }
    }

    /** Effect on {@link #accountId}'s balance: income credits, expense debits. */
    public Money signedAmount() {
        return switch (kind) {
            case INCOME -> amount;
            case EXPENSE, TRANSFER -> amount.negate();
        };
    }

    /** @return true if the state changed */
    public boolean complete(UUID saga, Instant now) {
        if (status == TransactionStatus.COMPLETED) {
            return false;
        }
        requirePending("complete");
        status = TransactionStatus.COMPLETED;
        sagaId = saga;
        completedAt = now;
        updatedAt = now;
        return true;
    }

    /** @return true if the state changed */
    public boolean cancel(String code, String reason, Instant now) {
        if (status == TransactionStatus.CANCELLED) {
            return false;
        }
        requirePending("cancel");
        status = TransactionStatus.CANCELLED;
        failureCode = code;
        failureReason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
        updatedAt = now;
        return true;
    }

    private void requirePending(String action) {
        if (status != TransactionStatus.PENDING) {
            throw new DomainException(TransactionErrors.INVALID_STATE_TRANSITION,
                    "Cannot " + action + " a " + status + " transaction");
        }
    }

    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public TransactionKind kind() { return kind; }
    public TransactionStatus status() { return status; }
    public UUID accountId() { return accountId; }
    public UUID counterAccountId() { return counterAccountId; }
    public Money amount() { return amount; }
    public UUID categoryId() { return categoryId; }
    public UUID incomeSourceId() { return incomeSourceId; }
    public LocalDate occurredOn() { return occurredOn; }
    public String description() { return description; }
    public String payee() { return payee; }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestHash() { return requestHash; }
    public UUID sagaId() { return sagaId; }
    public String failureCode() { return failureCode; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
}
