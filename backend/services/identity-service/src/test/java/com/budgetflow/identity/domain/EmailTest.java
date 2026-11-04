package com.budgetflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    void normalisesCaseAndWhitespace() {
        assertThat(Email.of("  Alice.Smith@Example.COM ").value()).isEqualTo("alice.smith@example.com");
    }

    @Test
    void exposesLocalPart() {
        assertThat(Email.of("alice@example.com").localPart()).isEqualTo("alice");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plainaddress", "@example.com", "alice@", "alice@example", "a b@example.com", "alice@@example.com"})
    void rejectsInvalidAddresses(String raw) {
        assertThatThrownBy(() -> Email.of(raw))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(IdentityErrors.INVALID_EMAIL);
    }

    @Test
    void rejectsAddressesLongerThan254Characters() {
        String tooLong = "a".repeat(64) + "@" + "b".repeat(186) + ".com";
        assertThatThrownBy(() -> Email.of(tooLong)).isInstanceOf(DomainException.class);
    }
}
