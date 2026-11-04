package com.budgetflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.LockoutPolicy;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");

    private final UserRepository users = mock(UserRepository.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final LoginAttemptService service = new LoginAttemptService(
            users, audit, new LockoutPolicy(2, Duration.ofMinutes(15)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void failureForKnownUserIncrementsAndAuditsThenLocks() {
        User user = user();
        when(users.findByEmail(user.email())).thenReturn(Optional.of(user));

        service.recordFailure("alice@example.com", CLIENT);
        service.recordFailure("ALICE@example.com", CLIENT);

        assertThat(user.isLockedAt(NOW)).isTrue();
        assertThat(auditTypes()).containsExactly(
                AuditEventType.LOGIN_FAILURE, AuditEventType.LOGIN_FAILURE, AuditEventType.ACCOUNT_LOCKED);
    }

    @Test
    void failureForUnknownOrMalformedLoginIsAuditedWithoutUser() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        service.recordFailure("nobody@example.com", CLIENT);
        service.recordFailure("not an email", CLIENT);

        verify(users, never()).update(any());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(e -> {
            assertThat(e.userId()).isNull();
            assertThat(e.type()).isEqualTo(AuditEventType.LOGIN_FAILURE);
        });
    }

    @Test
    void successResetsCountersAndAudits() {
        User user = user();
        user.recordFailedLogin(NOW, new LockoutPolicy(5, Duration.ofMinutes(1)));
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        service.recordSuccess(user.id(), CLIENT);

        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.lastLoginAt()).isEqualTo(NOW);
        verify(users).update(user);
        assertThat(auditTypes()).containsExactly(AuditEventType.LOGIN_SUCCESS);
    }

    private List<AuditEventType> auditTypes() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).record(captor.capture());
        return captor.getAllValues().stream().map(AuditEvent::type).toList();
    }

    private static User user() {
        return User.register(UUID.randomUUID(), Email.of("alice@example.com"), "{argon2}h", "Alice",
                Currency.getInstance("INR"), ZoneId.of("Asia/Kolkata"), NOW);
    }
}
