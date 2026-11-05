package com.budgetflow.budget.application.port;

import com.budgetflow.budget.domain.PeriodRange;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConsumptionRepository {

    record Consumption(UUID id, UUID budgetId, UUID userId, PeriodRange period, UUID transactionId,
                       String operationKey, Money amount, UUID reversesConsumptionId, Instant createdAt) {}

    void insert(Consumption consumption);

    /** Original (non-reversal) consumptions recorded under the key. */
    List<Consumption> findOriginals(String operationKey);
}
