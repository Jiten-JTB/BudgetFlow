package com.budgetflow.identity.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.PasswordHasher;
import com.budgetflow.identity.application.port.UserEventPublisher;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently erases a user once they confirm with their password. Identity deletes its own data in this transaction;
 * the UserDeleted event (outbox) makes every other service erase theirs. The email can sign up again at once.
 */
public class DeleteAccountService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UserEventPublisher events;
    private final AuditLog audit;
    private final Clock clock;

    public DeleteAccountService(UserRepository users, PasswordHasher hasher, UserEventPublisher events, AuditLog audit,
                                Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID userId, String password, ClientInfo client) {
        User user = users.findById(userId)
                .orElseThrow(() -> new DomainException(IdentityErrors.USER_NOT_FOUND, "User not found"));
        if (password == null || !hasher.matches(password, user.passwordHash())) {
            throw new DomainException(IdentityErrors.INCORRECT_PASSWORD, "That password is not correct");
        }
        Instant now = clock.instant();
        users.deleteWithAllData(user);
        events.userDeleted(user.id(), now);
        // The trail keeps that a deletion happened, never who it was: no email, only the now-meaningless id.
        audit.record(new AuditEvent(user.id(), null, AuditEventType.USER_DELETED, client, now));
    }
}
