package com.budgetflow.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionCursorTest {

    @Test
    void roundTripsOpaqueUrlSafeCursor() {
        var cursor = new TransactionCursor(LocalDate.parse("2026-11-04"), UUID.randomUUID());

        String encoded = cursor.encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(TransactionCursor.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsTamperedCursor() {
        for (String bad : new String[] {"not-base64!!", "Zm9v", "MjAyNi0xMS0wNHxub3QtYS11dWlk"}) {
            assertThatThrownBy(() -> TransactionCursor.decode(bad))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.INVALID_CURSOR);
        }
    }
}
