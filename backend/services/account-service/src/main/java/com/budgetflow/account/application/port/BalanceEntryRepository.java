package com.budgetflow.account.application.port;

import com.budgetflow.account.domain.BalanceEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BalanceEntryRepository {

    void insert(BalanceEntry entry);

    Optional<BalanceEntry> findByOperationKey(String operationKey);

    Optional<BalanceEntry> findReversalOf(UUID entryId);

    /** Newest first; {@code before} is an exclusive entry-id cursor (UUIDv7 = ledger order). */
    List<BalanceEntry> page(UUID accountId, UUID before, int limit);
}
