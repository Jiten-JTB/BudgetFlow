package com.budgetflow.identity.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.domain.IdentityErrors;
import java.time.ZoneId;
import java.util.Currency;

final class Inputs {

    private Inputs() {}

    static Currency currency(String code) {
        try {
            if (code != null && code.matches("[A-Z]{3}")) {
                return Currency.getInstance(code);
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        throw new DomainException(IdentityErrors.INVALID_CURRENCY, "Unknown ISO 4217 currency: " + code);
    }

    /** Region-based IANA ids only (e.g. Asia/Kolkata); fixed offsets break DST-aware reporting. */
    static ZoneId timezone(String id) {
        if (id != null && ZoneId.getAvailableZoneIds().contains(id)) {
            return ZoneId.of(id);
        }
        throw new DomainException(IdentityErrors.INVALID_TIMEZONE, "Unknown IANA timezone: " + id);
    }
}
