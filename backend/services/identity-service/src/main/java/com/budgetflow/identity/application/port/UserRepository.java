package com.budgetflow.identity.application.port;

import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    /** @throws com.budgetflow.common.core.error.DomainException EMAIL_ALREADY_REGISTERED on a concurrent duplicate */
    void insert(User user);

    /** @throws org.springframework.dao.OptimisticLockingFailureException if the user changed since it was read */
    void update(User user);
}
