package com.budgetflow.identity.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.PasswordHasher;
import com.budgetflow.identity.application.port.UserEventPublisher;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.PasswordPolicy;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import org.springframework.transaction.annotation.Transactional;

public class RegisterUserService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UserEventPublisher events;
    private final AuditLog audit;
    private final EmailVerificationService verification;
    private final boolean verificationRequired;
    private final Clock clock;

    /** @param verificationRequired false only where no mail is configured: users then start verified */
    public RegisterUserService(UserRepository users, PasswordHasher hasher, UserEventPublisher events,
                               AuditLog audit, EmailVerificationService verification, boolean verificationRequired,
                               Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.events = events;
        this.audit = audit;
        this.verification = verification;
        this.verificationRequired = verificationRequired;
        this.clock = clock;
    }

    @Transactional
    public User register(RegisterUserCommand command, ClientInfo client) {
        // Validate everything cheap before the deliberately expensive password hash.
        Email email = Email.of(command.email());
        PasswordPolicy.validate(command.password(), email);
        String displayName = User.validDisplayName(command.displayName());
        Currency currency = Inputs.currency(command.baseCurrency());
        ZoneId timezone = Inputs.timezone(command.timezone());
        if (users.existsByEmail(email)) {
            throw new DomainException(IdentityErrors.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }

        Instant now = clock.instant();
        User user = User.register(UuidV7.generate(), email, hasher.hash(command.password()), displayName,
                currency, timezone, now);
        if (!verificationRequired) {
            user.markEmailVerified(now);
        }
        users.insert(user);
        events.userRegistered(user);
        audit.record(new AuditEvent(user.id(), email.value(), AuditEventType.USER_REGISTERED, client, now));
        if (verificationRequired) {
            verification.issue(user);
        }
        return user;
    }
}
