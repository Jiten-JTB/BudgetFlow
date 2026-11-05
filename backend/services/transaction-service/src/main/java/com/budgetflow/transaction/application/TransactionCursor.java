package com.budgetflow.transaction.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/** Keyset position (occurredOn, id), encoded opaque so clients cannot depend on its shape. */
public record TransactionCursor(LocalDate occurredOn, UUID id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((occurredOn + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static TransactionCursor decode(String encoded) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed cursor");
            }
            return new TransactionCursor(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new DomainException(TransactionErrors.INVALID_CURSOR, "Invalid cursor");
        }
    }
}
