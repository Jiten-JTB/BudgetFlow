package com.budgetflow.identity.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.LockoutPolicy;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/** Tracks interactive login outcomes: lockout counters plus the security audit trail. */
public class LoginAttemptService {

    private static final int MAX_PRINCIPAL_LENGTH = 254;
    private static final int MAX_RETRIES = 3;

    private final UserRepository users;
    private final AuditLog audit;
    private final LockoutPolicy policy;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, AuditLog audit, LockoutPolicy policy, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public void recordFailure(String submittedLogin, ClientInfo client) {
        Instant now = clock.instant();
        Optional<Email> email = parse(submittedLogin);
        if (email.isEmpty()) {
            audit.record(new AuditEvent(null, truncate(submittedLogin), AuditEventType.LOGIN_FAILURE, client, now));
            return;
        }
        // Concurrent failures for the same user race on the version column; reload and retry.
        for (int attempt = 1; ; attempt++) {
            Optional<User> found = users.findByEmail(email.get());
            if (found.isEmpty()) {
                audit.record(new AuditEvent(null, truncate(submittedLogin), AuditEventType.LOGIN_FAILURE, client, now));
                return;
            }
            User user = found.get();
            boolean locked = user.recordFailedLogin(now, policy);
            try {
                users.update(user);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES) {
                    throw e;
                }
                continue;
            }
            audit.record(new AuditEvent(user.id(), user.email().value(), AuditEventType.LOGIN_FAILURE, client, now));
            if (locked) {
                audit.record(new AuditEvent(user.id(), user.email().value(), AuditEventType.ACCOUNT_LOCKED, client, now));
            }
            return;
        }
    }

    @Transactional
    public void recordSuccess(UUID userId, ClientInfo client) {
        Instant now = clock.instant();
        users.findById(userId).ifPresent(user -> {
            user.recordSuccessfulLogin(now);
            users.update(user);
            audit.record(new AuditEvent(userId, user.email().value(), AuditEventType.LOGIN_SUCCESS, client, now));
        });
    }

    /** A login refused before the password was checked (locked or disabled account). */
    @Transactional
    public void recordBlocked(String submittedLogin, AuditEventType type, ClientInfo client) {
        UUID userId = parse(submittedLogin).flatMap(users::findByEmail).map(User::id).orElse(null);
        audit.record(new AuditEvent(userId, truncate(submittedLogin), type, client, clock.instant()));
    }

    private static Optional<Email> parse(String login) {
        try {
            return Optional.of(Email.of(login));
        } catch (DomainException e) {
            return Optional.empty();
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_PRINCIPAL_LENGTH ? value : value.substring(0, MAX_PRINCIPAL_LENGTH);
    }
}
