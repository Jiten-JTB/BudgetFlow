package com.budgetflow.account.application.port;

import com.budgetflow.account.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    /** @throws com.budgetflow.common.core.error.DomainException DUPLICATE_ACCOUNT_NAME */
    void insert(Account account);

    /** Optimistic; @throws org.springframework.dao.OptimisticLockingFailureException, DUPLICATE_ACCOUNT_NAME */
    void update(Account account);

    Optional<Account> findOwned(UUID userId, UUID accountId);

    /** Row lock (SELECT ... FOR UPDATE) held until the transaction ends; serialises balance changes. */
    Optional<Account> findOwnedForUpdate(UUID userId, UUID accountId);

    List<Account> listByUser(UUID userId, boolean includeArchived);

    boolean existsForUser(UUID userId);
}
