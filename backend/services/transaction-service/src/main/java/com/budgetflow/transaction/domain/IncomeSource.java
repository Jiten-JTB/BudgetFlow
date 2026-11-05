package com.budgetflow.transaction.domain;

import com.budgetflow.common.core.error.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Where income comes from: an employer, a business, a rental property... */
public final class IncomeSource {

    private final UUID id;
    private final UUID userId;
    private String name;
    private IncomeSourceType type;
    private UUID defaultAccountId;
    private String notes;
    private Instant archivedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private IncomeSource(UUID id, UUID userId, String name, IncomeSourceType type, UUID defaultAccountId, String notes,
                         Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.name = name;
        this.type = Objects.requireNonNull(type);
        this.defaultAccountId = defaultAccountId;
        this.notes = notes;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static IncomeSource create(UUID id, UUID userId, String name, IncomeSourceType type, UUID defaultAccountId,
                                      String notes, Instant now) {
        return new IncomeSource(id, userId, validName(name), type, defaultAccountId, validNotes(notes), null, now, now, 0);
    }

    public static IncomeSource rehydrate(UUID id, UUID userId, String name, IncomeSourceType type, UUID defaultAccountId,
                                         String notes, Instant archivedAt, Instant createdAt, Instant updatedAt,
                                         long version) {
        return new IncomeSource(id, userId, name, type, defaultAccountId, notes, archivedAt, createdAt, updatedAt, version);
    }

    /** {@code null} arguments leave the field unchanged. */
    public void update(String newName, IncomeSourceType newType, UUID newDefaultAccountId, String newNotes, Instant now) {
        if (isArchived()) {
            throw new DomainException(TransactionErrors.INCOME_SOURCE_ARCHIVED, "Income source is archived");
        }
        if (newName != null) {
            name = validName(newName);
        }
        if (newType != null) {
            type = newType;
        }
        if (newDefaultAccountId != null) {
            defaultAccountId = newDefaultAccountId;
        }
        if (newNotes != null) {
            notes = validNotes(newNotes);
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

    private static String validName(String raw) {
        return Text.required(raw, 100, TransactionErrors.INVALID_NAME, "Income source name");
    }

    private static String validNotes(String raw) {
        return Text.optional(raw, 500, TransactionErrors.INVALID_NAME, "Notes");
    }

    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String name() { return name; }
    public IncomeSourceType type() { return type; }
    public UUID defaultAccountId() { return defaultAccountId; }
    public String notes() { return notes; }
    public Instant archivedAt() { return archivedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
