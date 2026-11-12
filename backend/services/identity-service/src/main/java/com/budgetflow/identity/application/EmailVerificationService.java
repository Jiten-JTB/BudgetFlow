package com.budgetflow.identity.application;

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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Proves a person can read the inbox they signed up with. The link carries a random 256-bit token; only its SHA-256
 * hash is stored, it works once, and it expires after 24 hours.
 */
public class EmailVerificationService {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final VerificationTokenStore tokens;
    private final EmailOutbox outbox;
    private final AuditLog audit;
    private final Clock clock;
    private final String verifyUrl;

    public EmailVerificationService(UserRepository users, VerificationTokenStore tokens, EmailOutbox outbox,
                                    AuditLog audit, Clock clock, String verifyUrl) {
        this.users = users;
        this.tokens = tokens;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.verifyUrl = verifyUrl;
    }

    /** Replaces any earlier link with a fresh one and queues the email. Joins the caller's transaction. */
    @Transactional
    public void issue(User user) {
        Instant now = clock.instant();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteForUser(user.id());
        tokens.save(sha256(token), user.id(), now.plus(TOKEN_LIFETIME), now);
        String link = verifyUrl + "?token=" + token;
        outbox.enqueue(new EmailOutbox.Message(user.email().value(), "Confirm your email for Budget Flow",
                text(user.displayName(), link), html(user.displayName(), link)));
    }

    @Transactional
    public void confirm(String token) {
        Instant now = clock.instant();
        User user = tokens.findUserByValidHash(sha256(token == null ? "" : token), now)
                .flatMap(users::findById)
                .orElseThrow(() -> new DomainException(IdentityErrors.INVALID_VERIFICATION_TOKEN,
                        "This link has expired or was already used"));
        user.markEmailVerified(now);
        users.update(user);
        tokens.deleteForUser(user.id());
        audit.record(new AuditEvent(user.id(), user.email().value(), AuditEventType.EMAIL_VERIFIED, null, now));
    }

    /** Silent by design: the caller learns nothing about whether the address has an account. */
    @Transactional
    public void resend(String rawEmail) {
        Email email;
        try {
            email = Email.of(rawEmail);
        } catch (DomainException e) {
            return;
        }
        Instant now = clock.instant();
        users.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .filter(user -> tokens.latestIssuedAt(user.id())
                        .map(last -> !now.isBefore(last.plus(RESEND_INTERVAL))).orElse(true))
                .ifPresent(this::issue);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String name, String link) {
        return """
                Hello %s,

                Confirm your email to finish setting up Budget Flow:
                %s

                The link works once and expires in 24 hours. If you did not sign up, ignore this email.
                """.formatted(name, link);
    }

    private static String html(String name, String link) {
        String safeName = HtmlUtils.htmlEscape(name);
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html><html><body style="margin:0;background:#f4f3ef;font-family:Mukta,Segoe UI,Helvetica,Arial,sans-serif;color:#33322e">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px">
                <table role="presentation" width="520" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:10px;border-top:4px solid #9e2a20">
                <tr><td style="padding:32px">
                <p style="margin:0 0 24px;font-size:18px;font-weight:600;color:#26305c">Budget Flow</p>
                <p style="margin:0 0 16px;font-size:16px">Hello %s,</p>
                <p style="margin:0 0 24px;font-size:16px;line-height:1.5">Confirm your email to finish setting up your books.</p>
                <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;background:#26305c;color:#ffffff;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:6px">Confirm my email</a></p>
                <p style="margin:0 0 8px;font-size:14px;color:#66635c;line-height:1.5">The link works once and expires in 24 hours. If you did not sign up, ignore this email.</p>
                <p style="margin:0;font-size:13px;color:#66635c;word-break:break-all">%s</p>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(safeName, safeLink, safeLink);
    }
}
