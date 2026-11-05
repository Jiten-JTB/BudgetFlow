package com.budgetflow.transaction.application.port;

import com.budgetflow.transaction.domain.IncomeSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeSourceRepository {

    /** @throws com.budgetflow.common.core.error.DomainException DUPLICATE_NAME */
    void insert(IncomeSource source);

    void update(IncomeSource source);

    Optional<IncomeSource> findOwned(UUID userId, UUID sourceId);

    List<IncomeSource> list(UUID userId, boolean includeArchived);
}
