package com.budgetflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private static final Email EMAIL = Email.of("alice.smith@example.com");

    @Test
    void acceptsLongPassphrases() {
        assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple", EMAIL)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "elevenchars"})
    void rejectsPasswordsShorterThan12Characters(String raw) {
        assertWeak(raw);
    }

    @Test
    void rejectsPasswordsLongerThan128Characters() {
        assertWeak("x1".repeat(65));
    }

    @Test
    void rejectsSingleRepeatedCharacter() {
        assertWeak("aaaaaaaaaaaaaaaa");
    }

    @Test
    void rejectsPasswordsContainingTheEmailLocalPart() {
        assertWeak("my-ALICE.SMITH-password");
    }

    @Test
    void rejectsNull() {
        assertWeak(null);
    }

    private static void assertWeak(String raw) {
        assertThatThrownBy(() -> PasswordPolicy.validate(raw, EMAIL))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(IdentityErrors.WEAK_PASSWORD);
    }
}
