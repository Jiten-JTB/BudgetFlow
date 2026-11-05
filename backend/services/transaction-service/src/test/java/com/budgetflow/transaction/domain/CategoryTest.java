package com.budgetflow.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Test
    void normalisesColourAndValidatesIcon() {
        Category c = Category.create(UUID.randomUUID(), UUID.randomUUID(), " Food ", CategoryKind.EXPENSE, null,
                "utensils", "#ff8800", NOW);

        assertThat(c.name()).isEqualTo("Food");
        assertThat(c.color()).isEqualTo("#FF8800");
        assertThat(c.icon()).isEqualTo("utensils");
    }

    @Test
    void rejectsBadColourIconAndName() {
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> Category.create(UUID.randomUUID(), user, "Food", CategoryKind.EXPENSE, null, null, "red", NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.INVALID_COLOR);
        assertThatThrownBy(() -> Category.create(UUID.randomUUID(), user, "Food", CategoryKind.EXPENSE, null, "<svg>", null, NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.INVALID_ICON);
        assertThatThrownBy(() -> Category.create(UUID.randomUUID(), user, "x".repeat(61), CategoryKind.EXPENSE, null, null, null, NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.INVALID_NAME);
    }

    @Test
    void archiveIsIdempotentAndBlocksEdits() {
        Category c = Category.create(UUID.randomUUID(), UUID.randomUUID(), "Food", CategoryKind.EXPENSE, null, null, null, NOW);
        c.archive(NOW);
        c.archive(NOW.plusSeconds(1));

        assertThat(c.archivedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> c.update("New", null, null, NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.CATEGORY_ARCHIVED);
    }
}
