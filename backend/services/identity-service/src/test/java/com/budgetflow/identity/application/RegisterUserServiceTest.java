package com.budgetflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.PasswordHasher;
import com.budgetflow.identity.application.port.UserEventPublisher;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final UserEventPublisher events = mock(UserEventPublisher.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final EmailVerificationService verification = mock(EmailVerificationService.class);
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        when(hasher.hash(any())).thenReturn("{argon2}hashed");
        service = new RegisterUserService(users, hasher, events, audit, verification, true, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersUserWithHashedPasswordAndPublishesEvent() {
        User user = service.register(command("Alice@Example.com", "correct horse battery"), CLIENT);

        assertThat(user.id().version()).isEqualTo(7);
        assertThat(user.email().value()).isEqualTo("alice@example.com");
        assertThat(user.passwordHash()).isEqualTo("{argon2}hashed");
        assertThat(user.createdAt()).isEqualTo(NOW);
        verify(hasher).hash("correct horse battery");
        verify(users).insert(user);
        verify(events).userRegistered(user);
        ArgumentCaptor<AuditEvent> auditEvent = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(auditEvent.capture());
        assertThat(auditEvent.getValue().type()).isEqualTo(AuditEventType.USER_REGISTERED);
        assertThat(auditEvent.getValue().userId()).isEqualTo(user.id());
        assertThat(auditEvent.getValue().client()).isEqualTo(CLIENT);
    }

    @Test
    void rejectsAlreadyRegisteredEmailWithoutHashing() {
        when(users.existsByEmail(Email.of("alice@example.com"))).thenReturn(true);

        assertError(() -> service.register(command("ALICE@example.com", "correct horse battery"), CLIENT),
                IdentityErrors.EMAIL_ALREADY_REGISTERED);
        verify(hasher, never()).hash(any());
        verify(users, never()).insert(any());
        verify(events, never()).userRegistered(any());
    }

    @Test
    void rejectsWeakPasswordBeforeHashing() {
        assertError(() -> service.register(command("alice@example.com", "short"), CLIENT), IdentityErrors.WEAK_PASSWORD);
        verify(hasher, never()).hash(any());
    }

    @Test
    void rejectsUnknownCurrencyAndTimezone() {
        assertError(() -> service.register(new RegisterUserCommand(
                "alice@example.com", "correct horse battery", "Alice", "XYZ1", "Asia/Kolkata"), CLIENT),
                IdentityErrors.INVALID_CURRENCY);
        assertError(() -> service.register(new RegisterUserCommand(
                "alice@example.com", "correct horse battery", "Alice", "INR", "Mars/Olympus"), CLIENT),
                IdentityErrors.INVALID_TIMEZONE);
    }

    @Test
    void sendsAVerificationEmailAndLeavesTheUserUnverified() {
        User user = service.register(command("bob@example.com", "correct horse battery"), CLIENT);
        assertThat(user.isEmailVerified()).isFalse();
        verify(verification).issue(user);
    }

    @Test
    void whenVerificationIsSwitchedOffUsersStartVerifiedAndNoEmailIsSent() {
        service = new RegisterUserService(users, hasher, events, audit, verification, false, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = service.register(command("bob@example.com", "correct horse battery"), CLIENT);
        assertThat(user.isEmailVerified()).isTrue();
        verify(verification, never()).issue(any());
    }

    private static RegisterUserCommand command(String email, String password) {
        return new RegisterUserCommand(email, password, "Alice", "INR", "Asia/Kolkata");
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
