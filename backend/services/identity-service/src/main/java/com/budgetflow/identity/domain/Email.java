package com.budgetflow.identity.domain;

import com.budgetflow.common.core.error.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalised (trimmed, lower-case) email address; the login identifier. */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    // Deliberately simple: one '@', no whitespace, a dot in the domain. Ownership is proven by verification, not regex.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new DomainException(IdentityErrors.INVALID_EMAIL, "Invalid email address");
        }
    }

    public static Email of(String raw) {
        return new Email(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    }

    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }
}
