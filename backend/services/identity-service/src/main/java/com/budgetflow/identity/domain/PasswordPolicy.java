package com.budgetflow.identity.domain;

import com.budgetflow.common.core.error.DomainException;
import java.util.Locale;

/** NIST SP 800-63B style: length over composition rules, plus checks against trivially guessable choices. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final int MIN_LOCAL_PART_TO_CHECK = 3;

    private PasswordPolicy() {}

    public static void validate(String raw, Email email) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw weak("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (raw.length() > MAX_LENGTH) {
            throw weak("Password must be at most " + MAX_LENGTH + " characters");
        }
        if (raw.chars().distinct().count() == 1) {
            throw weak("Password must not be a single repeated character");
        }
        String localPart = email.localPart();
        if (localPart.length() >= MIN_LOCAL_PART_TO_CHECK && raw.toLowerCase(Locale.ROOT).contains(localPart)) {
            throw weak("Password must not contain your email address");
        }
    }

    private static DomainException weak(String message) {
        return new DomainException(IdentityErrors.WEAK_PASSWORD, message);
    }
}
