package com.budgetflow.budget.application;

import com.budgetflow.budget.application.port.BudgetRepository;
import com.budgetflow.budget.application.port.UsageRepository;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetErrors;
import com.budgetflow.budget.domain.BudgetPeriod;
import com.budgetflow.budget.domain.BudgetStatus;
import com.budgetflow.budget.domain.BudgetUsage;
import com.budgetflow.budget.domain.PeriodRange;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.common.core.money.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class BudgetService {

    static final int DEFAULT_ALERT_THRESHOLD = 80;

    private final BudgetRepository budgets;
    private final UsageRepository usages;
    private final Clock clock;

    public BudgetService(BudgetRepository budgets, UsageRepository usages, Clock clock) {
        this.budgets = budgets;
        this.usages = usages;
        this.clock = clock;
    }

    @Transactional
    public Budget create(UUID userId, CreateBudgetCommand command) {
        BudgetPeriod period = Inputs.period(command.period());
        Currency currency = Inputs.currency(command.currency());
        Money limit = Inputs.money(command.limit(), currency);
        LocalDate startsOn = Inputs.date(command.startsOn());
        if (startsOn == null) {
            startsOn = period.rangeContaining(today()).start();
        }
        int threshold = command.alertThresholdPercent() == null ? DEFAULT_ALERT_THRESHOLD : command.alertThresholdPercent();
        Budget budget = Budget.create(UuidV7.generate(), userId, command.name(), command.categoryId(), period, limit,
                threshold, startsOn, clock.instant());
        budgets.insert(budget);
        return budget;
    }

    @Transactional(readOnly = true)
    public Budget get(UUID userId, UUID budgetId) {
        return budgets.findOwned(userId, budgetId).orElseThrow(BudgetService::notFound);
    }

    @Transactional(readOnly = true)
    public List<Budget> list(UUID userId, boolean includeArchived) {
        return budgets.list(userId, includeArchived);
    }

    @Transactional
    public Budget update(UUID userId, UUID budgetId, UpdateBudgetCommand command) {
        Budget budget = get(userId, budgetId);
        Money limit = command.limit() == null ? null : Inputs.money(command.limit(), budget.currency());
        budget.update(command.name(), limit, command.alertThresholdPercent(), clock.instant());
        budgets.update(budget);
        return budget;
    }

    @Transactional
    public Budget archive(UUID userId, UUID budgetId) {
        Budget budget = get(userId, budgetId);
        budget.archive(clock.instant());
        budgets.update(budget);
        return budget;
    }

    /** @param date any day in the period of interest; null = today (UTC) */
    @Transactional(readOnly = true)
    public BudgetStatus status(UUID userId, UUID budgetId, String date) {
        return statusOf(get(userId, budgetId), dateOrToday(date));
    }

    @Transactional(readOnly = true)
    public List<BudgetStatus> statuses(UUID userId, String date) {
        LocalDate day = dateOrToday(date);
        return budgets.list(userId, false).stream().map(b -> statusOf(b, day)).toList();
    }

    BudgetStatus statusOf(Budget budget, LocalDate date) {
        PeriodRange period = budget.period().rangeContaining(date);
        Money spent = usages.find(budget, period).map(BudgetUsage::spent).orElse(Money.of(java.math.BigDecimal.ZERO, budget.currency()));
        return new BudgetStatus(budget, period, spent);
    }

    private LocalDate dateOrToday(String date) {
        LocalDate parsed = Inputs.date(date);
        return parsed == null ? today() : parsed;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static DomainException notFound() {
        return new DomainException(BudgetErrors.BUDGET_NOT_FOUND, "Budget not found");
    }
}
