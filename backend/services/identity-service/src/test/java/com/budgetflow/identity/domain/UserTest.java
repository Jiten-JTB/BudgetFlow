package com.budgetflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final LockoutPolicy POLICY = new LockoutPolicy(3, Duration.ofMinutes(15));

    @Test
    void registersActiveUserWithTrimmedDisplayName() {
        User user = newUser();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.displayName()).isEqualTo("Alice");
        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.updatedAt()).isEqualTo(NOW);
        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void rejectsBlankOrOverlongOrControlCharacterDisplayNames() {
        for (String bad : new String[] {"  ", "x".repeat(101), "Ali\u0000ce", null}) {
            assertThatThrownBy(() -> User.register(UUID.randomUUID(), Email.of("a@example.com"), "{argon2}h", bad,
                    Currency.getInstance("INR"), ZoneId.of("Asia/Kolkata"), NOW))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(IdentityErrors.INVALID_DISPLAY_NAME);
        }
    }

    @Test
    void locksAfterMaxFailedAttempts() {
        User user = newUser();

        assertThat(user.recordFailedLogin(NOW, POLICY)).isFalse();
        assertThat(user.recordFailedLogin(NOW, POLICY)).isFalse();
        assertThat(user.recordFailedLogin(NOW, POLICY)).as("third failure locks").isTrue();

        assertThat(user.isLockedAt(NOW.plusSeconds(60))).isTrue();
        assertThat(user.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(user.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).as("unlocks when duration elapses").isFalse();
    }

    @Test
    void failureAfterExpiredLockStartsFreshWindow() {
        User user = newUser();
        for (int i = 0; i < 3; i++) {
            user.recordFailedLogin(NOW, POLICY);
        }
        Instant afterLock = NOW.plus(Duration.ofMinutes(20));

        boolean locked = user.recordFailedLogin(afterLock, POLICY);

        assertThat(locked).isFalse();
        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLockedAt(afterLock)).isFalse();
    }

    @Test
    void successfulLoginResetsCountersAndRecordsTime() {
        User user = newUser();
        user.recordFailedLogin(NOW, POLICY);

        user.recordSuccessfulLogin(NOW.plusSeconds(5));

        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.lockedUntil()).isNull();
        assertThat(user.lastLoginAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void updatesOnlyProvidedProfileFields() {
        User user = newUser();
        Instant later = NOW.plusSeconds(10);

        user.updateProfile(null, Currency.getInstance("USD"), null, later);

        assertThat(user.displayName()).isEqualTo("Alice");
        assertThat(user.baseCurrency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(user.timezone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(user.updatedAt()).isEqualTo(later);
    }

    @Test
    void aNewUserHasNotConfirmedTheirEmail() {
        User user = newUser();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.emailVerifiedAt()).isNull();
    }

    @Test
    void confirmingTheEmailRecordsWhenAndIsIdempotent() {
        User user = newUser();
        user.markEmailVerified(NOW.plusSeconds(60));
        user.markEmailVerified(NOW.plusSeconds(600));
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.emailVerifiedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    private static User newUser() {
        return User.register(UUID.randomUUID(), Email.of("alice@example.com"), "{argon2}hash", "  Alice ",
                Currency.getInstance("INR"), ZoneId.of("Asia/Kolkata"), NOW);
    }
}
