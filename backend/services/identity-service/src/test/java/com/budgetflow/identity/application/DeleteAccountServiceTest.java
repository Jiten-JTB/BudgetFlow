package com.budgetflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetflow.common.core.error.DomainException;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeleteAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "JUnit");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final UserEventPublisher events = mock(UserEventPublisher.class);
    private final AuditLog audit = mock(AuditLog.class);
    private DeleteAccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new DeleteAccountService(users, hasher, events, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.register(UUID.randomUUID(), Email.of("asha@example.com"), "{argon2}h", "Asha",
                Currency.getInstance("INR"), ZoneId.of("Asia/Kolkata"), NOW);
        when(users.findById(user.id())).thenReturn(Optional.of(user));
    }

    @Test
    void theWrongPasswordDeletesNothing() {
        when(hasher.matches("wrong", "{argon2}h")).thenReturn(false);
        assertThatThrownBy(() -> service.delete(user.id(), "wrong", CLIENT)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(IdentityErrors.INCORRECT_PASSWORD);
        verify(users, never()).deleteWithAllData(any());
        verify(events, never()).userDeleted(any(), any());
    }

    @Test
    void theRightPasswordErasesTheUserTellsOtherServicesAndKeepsNoPersonalDataInTheAudit() {
        when(hasher.matches("correct horse", "{argon2}h")).thenReturn(true);

        service.delete(user.id(), "correct horse", CLIENT);

        verify(users).deleteWithAllData(user);
        verify(events).userDeleted(user.id(), NOW);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AuditEventType.USER_DELETED);
        assertThat(event.getValue().principal()).isNull();
    }

    @Test
    void anUnknownUserIsNotFound() {
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(ghost, "x", CLIENT)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(IdentityErrors.USER_NOT_FOUND);
    }
}
