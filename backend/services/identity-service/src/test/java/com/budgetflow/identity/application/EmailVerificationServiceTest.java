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
import com.budgetflow.identity.application.port.EmailOutbox;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.application.port.VerificationTokenStore;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final String VERIFY_URL = "https://budgetflow.test/verify-email";

    private final UserRepository users = mock(UserRepository.class);
    private final VerificationTokenStore tokens = mock(VerificationTokenStore.class);
    private final EmailOutbox outbox = mock(EmailOutbox.class);
    private final AuditLog audit = mock(AuditLog.class);
    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(users, tokens, outbox, audit, Clock.fixed(NOW, ZoneOffset.UTC), VERIFY_URL);
        user = User.register(UUID.randomUUID(), Email.of("asha@example.com"), "{argon2}h", "Asha",
                Currency.getInstance("INR"), ZoneId.of("Asia/Kolkata"), NOW.minusSeconds(3600));
    }

    @Test
    void issuingStoresOnlyTheTokensHashAndEmailsALinkWithTheTokenItself() throws Exception {
        service.issue(user);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(tokens).deleteForUser(user.id());
        verify(tokens).save(hash.capture(), any(), any(), any());
        ArgumentCaptor<EmailOutbox.Message> mail = ArgumentCaptor.forClass(EmailOutbox.Message.class);
        verify(outbox).enqueue(mail.capture());

        assertThat(mail.getValue().to()).isEqualTo("asha@example.com");
        String token = mail.getValue().text().replaceAll("(?s).*verify-email\\?token=([A-Za-z0-9_-]+).*", "$1");
        assertThat(token).hasSizeGreaterThanOrEqualTo(43);           // 32 random bytes, base64url
        assertThat(hash.getValue()).isEqualTo(sha256(token)).doesNotContain(token);
        assertThat(mail.getValue().html()).contains(VERIFY_URL + "?token=" + token);
    }

    @Test
    void issuedTokensExpireAfter24Hours() {
        service.issue(user);
        verify(tokens).save(any(), any(), org.mockito.ArgumentMatchers.eq(NOW.plusSeconds(24 * 3600)),
                org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void confirmingAValidTokenVerifiesTheUserAndConsumesEveryToken() throws Exception {
        when(tokens.findUserByValidHash(sha256("tok"), NOW)).thenReturn(Optional.of(user.id()));
        when(users.findById(user.id())).thenReturn(Optional.of(user));

        service.confirm("tok");

        assertThat(user.isEmailVerified()).isTrue();
        verify(users).update(user);
        verify(tokens).deleteForUser(user.id());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AuditEventType.EMAIL_VERIFIED);
    }

    @Test
    void anUnknownOrExpiredTokenIsRefusedInPlainTerms() {
        when(tokens.findUserByValidHash(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm("nope")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(IdentityErrors.INVALID_VERIFICATION_TOKEN);
        verify(users, never()).update(any());
    }

    @Test
    void resendingSaysNothingAboutWhetherTheEmailExists() {
        when(users.findByEmail(Email.of("ghost@example.com"))).thenReturn(Optional.empty());
        service.resend("ghost@example.com");
        service.resend("not an email");
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void resendingSkipsVerifiedUsersAndThrottlesToOncePerMinute() {
        when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(tokens.latestIssuedAt(user.id())).thenReturn(Optional.of(NOW.minusSeconds(30)));
        service.resend("asha@example.com");
        verify(outbox, never()).enqueue(any());

        when(tokens.latestIssuedAt(user.id())).thenReturn(Optional.of(NOW.minusSeconds(61)));
        service.resend("ASHA@example.com ");
        verify(outbox).enqueue(any());

        user.markEmailVerified(NOW);
        service.resend("asha@example.com");
        verify(outbox).enqueue(any()); // still only the one above
    }

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
