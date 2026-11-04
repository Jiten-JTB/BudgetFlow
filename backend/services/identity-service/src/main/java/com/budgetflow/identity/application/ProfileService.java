package com.budgetflow.identity.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.application.port.AuditLog;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.AuditEventType;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ProfileService {

    private final UserRepository users;
    private final AuditLog audit;
    private final Clock clock;

    public ProfileService(UserRepository users, AuditLog audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new DomainException(IdentityErrors.USER_NOT_FOUND, "User not found"));
    }

    @Transactional
    public User update(UUID userId, UpdateProfileCommand command, ClientInfo client) {
        User user = get(userId);
        Instant now = clock.instant();
        user.updateProfile(
                command.displayName(),
                command.baseCurrency() == null ? null : Inputs.currency(command.baseCurrency()),
                command.timezone() == null ? null : Inputs.timezone(command.timezone()),
                now);
        users.update(user);
        audit.record(new AuditEvent(userId, user.email().value(), AuditEventType.PROFILE_UPDATED, client, now));
        return user;
    }
}
