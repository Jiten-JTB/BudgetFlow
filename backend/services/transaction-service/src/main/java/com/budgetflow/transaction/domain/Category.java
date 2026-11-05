package com.budgetflow.transaction.domain;

import com.budgetflow.common.core.error.DomainException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Category {

    private static final Pattern COLOR = Pattern.compile("#[0-9A-F]{6}");
    private static final Pattern ICON = Pattern.compile("[a-z0-9-]{1,40}");

    private final UUID id;
    private final UUID userId;
    private String name;
    private final CategoryKind kind;
    private final UUID parentId;
    private String icon;
    private String color;
    private Instant archivedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Category(UUID id, UUID userId, String name, CategoryKind kind, UUID parentId, String icon, String color,
                     Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.name = name;
        this.kind = Objects.requireNonNull(kind);
        this.parentId = parentId;
        this.icon = icon;
        this.color = color;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static Category create(UUID id, UUID userId, String name, CategoryKind kind, UUID parentId, String icon,
                                  String color, Instant now) {
        return new Category(id, userId, validName(name), kind, parentId, validIcon(icon), validColor(color),
                null, now, now, 0);
    }

    public static Category rehydrate(UUID id, UUID userId, String name, CategoryKind kind, UUID parentId, String icon,
                                     String color, Instant archivedAt, Instant createdAt, Instant updatedAt,
                                     long version) {
        return new Category(id, userId, name, kind, parentId, icon, color, archivedAt, createdAt, updatedAt, version);
    }

    /** {@code null} arguments leave the field unchanged. */
    public void update(String newName, String newIcon, String newColor, Instant now) {
        if (isArchived()) {
            throw new DomainException(TransactionErrors.CATEGORY_ARCHIVED, "Category is archived");
        }
        if (newName != null) {
            name = validName(newName);
        }
        if (newIcon != null) {
            icon = validIcon(newIcon);
        }
        if (newColor != null) {
            color = validColor(newColor);
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

    public boolean isTopLevel() {
        return parentId == null;
    }

    private static String validName(String raw) {
        return Text.required(raw, 60, TransactionErrors.INVALID_NAME, "Category name");
    }

    private static String validIcon(String raw) {
        if (raw == null) {
            return null;
        }
        if (!ICON.matcher(raw).matches()) {
            throw new DomainException(TransactionErrors.INVALID_ICON, "Icon must be 1-40 chars of a-z, 0-9, -");
        }
        return raw;
    }

    private static String validColor(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.toUpperCase(Locale.ROOT);
        if (!COLOR.matcher(normalised).matches()) {
            throw new DomainException(TransactionErrors.INVALID_COLOR, "Colour must be #RRGGBB");
        }
        return normalised;
    }

    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String name() { return name; }
    public CategoryKind kind() { return kind; }
    public UUID parentId() { return parentId; }
    public String icon() { return icon; }
    public String color() { return color; }
    public Instant archivedAt() { return archivedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
