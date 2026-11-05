package com.budgetflow.budget.application;

import com.budgetflow.budget.application.port.BudgetEventPublisher;
import com.budgetflow.budget.application.port.BudgetRepository;
import com.budgetflow.budget.application.port.ConsumptionRepository;
import com.budgetflow.budget.application.port.ConsumptionRepository.Consumption;
import com.budgetflow.budget.application.port.OperationLog;
import com.budgetflow.budget.application.port.UsageRepository;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetErrors;
import com.budgetflow.budget.domain.BudgetStatus;
import com.budgetflow.budget.domain.BudgetUsage;
import com.budgetflow.budget.domain.PeriodRange;
import com.budgetflow.budget.domain.UsageChange;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga participant operations on budget usage. Idempotent per operation key, serialised per key, and
 * release (compensation) is safe in any order relative to consume.
 */
public class ConsumptionService {

    private final BudgetRepository budgets;
    private final UsageRepository usages;
    private final ConsumptionRepository consumptions;
    private final OperationLog operations;
    private final BudgetEventPublisher events;
    private final BudgetService budgetService;
    private final Clock clock;

    public ConsumptionService(BudgetRepository budgets, UsageRepository usages, ConsumptionRepository consumptions,
                              OperationLog operations, BudgetEventPublisher events, BudgetService budgetService,
                              Clock clock) {
        this.budgets = budgets;
        this.usages = usages;
        this.consumptions = consumptions;
        this.operations = operations;
        this.events = events;
        this.budgetService = budgetService;
        this.clock = clock;
    }

    @Transactional
    public ConsumptionResult consume(ConsumeBudgetCommand command) {
        if (!command.amount().isPositive()) {
            throw new DomainException(BudgetErrors.INVALID_AMOUNT, "Expense amount must be positive");
        }
        operations.lock(command.operationKey());
        Optional<OperationLog.Status> previous = operations.find(command.operationKey());
        if (previous.isPresent()) {
            if (previous.get() == OperationLog.Status.CANCELLED) {
                throw new DomainException(BudgetErrors.OPERATION_CANCELLED,
                        "Operation " + command.operationKey() + " was cancelled");
            }
            return new ConsumptionResult(statusesFor(command.userId(), command.operationKey(), command), true);
        }

        Instant now = clock.instant();
        List<UUID> scopes = Stream.of(command.categoryId(), command.parentCategoryId())
                .filter(java.util.Objects::nonNull).toList();
        List<BudgetStatus> statuses = new ArrayList<>();
        // findMatching is ordered by id, so concurrent consumers lock usage rows in the same order (no deadlocks).
        for (Budget budget : budgets.findMatching(command.userId(), command.amount().currency(), command.occurredOn(), scopes)) {
            PeriodRange period = budget.period().rangeContaining(command.occurredOn());
            BudgetUsage usage = usages.lockOrCreate(budget, period, now);
            UsageChange change = usage.add(command.amount(), budget, now);
            usages.update(usage);
            consumptions.insert(new Consumption(UuidV7.generate(), budget.id(), command.userId(), period,
                    command.transactionId(), command.operationKey(), command.amount(), null, now));
            BudgetStatus status = new BudgetStatus(budget, period, usage.spent());
            if (change.thresholdReached()) {
                events.thresholdReached(status);
            }
            if (change.exceeded()) {
                events.exceeded(status);
            }
            statuses.add(status);
        }
        operations.record(command.operationKey(), command.userId(), OperationLog.Status.APPLIED, now);
        return new ConsumptionResult(statuses, false);
    }

    @Transactional
    public ReleaseOutcome release(UUID userId, String operationKey) {
        operations.lock(operationKey);
        Instant now = clock.instant();
        Optional<OperationLog.Status> previous = operations.find(operationKey);
        if (previous.isEmpty()) {
            operations.record(operationKey, userId, OperationLog.Status.CANCELLED, now);
            return ReleaseOutcome.NOTHING_TO_RELEASE;
        }
        switch (previous.get()) {
            case REVERSED: return ReleaseOutcome.ALREADY_RELEASED;
            case CANCELLED: return ReleaseOutcome.NOTHING_TO_RELEASE;
            case APPLIED: break;
        }
        for (Consumption original : consumptions.findOriginals(operationKey)) {
            if (!original.userId().equals(userId)) {
                continue;
            }
            Budget budget = budgets.findOwned(userId, original.budgetId()).orElseThrow(BudgetService::notFound);
            BudgetUsage usage = usages.lockOrCreate(budget, original.period(), now);
            usage.add(original.amount().negate(), budget, now);
            usages.update(usage);
            consumptions.insert(new Consumption(UuidV7.generate(), budget.id(), userId, original.period(),
                    original.transactionId(), operationKey + ":release", original.amount().negate(), original.id(), now));
        }
        operations.record(operationKey, userId, OperationLog.Status.REVERSED, now);
        return ReleaseOutcome.RELEASED;
    }

    private List<BudgetStatus> statusesFor(UUID userId, String operationKey, ConsumeBudgetCommand command) {
        return consumptions.findOriginals(operationKey).stream()
                .filter(c -> c.userId().equals(userId))
                .map(c -> budgetService.statusOf(
                        budgets.findOwned(userId, c.budgetId()).orElseThrow(BudgetService::notFound),
                        command.occurredOn()))
                .toList();
    }
}
