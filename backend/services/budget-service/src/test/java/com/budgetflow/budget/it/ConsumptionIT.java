package com.budgetflow.budget.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.budget.application.BudgetService;
import com.budgetflow.budget.application.ConsumeBudgetCommand;
import com.budgetflow.budget.application.ConsumptionResult;
import com.budgetflow.budget.application.ConsumptionService;
import com.budgetflow.budget.application.CreateBudgetCommand;
import com.budgetflow.budget.application.ReleaseOutcome;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetErrors;
import com.budgetflow.budget.domain.BudgetStatus;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Saga-facing consume/release operations against the real database. */
class ConsumptionIT extends BudgetIntegrationTest {

    private static final LocalDate NOV_10 = LocalDate.parse("2026-11-10");

    @Autowired BudgetService budgets;
    @Autowired ConsumptionService consumption;
    @Autowired JdbcClient jdbc;

    private final UUID user = UUID.randomUUID();
    private final UUID dining = UUID.randomUUID();     // sub-category of food
    private final UUID food = UUID.randomUUID();
    private Budget foodBudget;
    private Budget overall;

    @BeforeEach
    void setUp() {
        foodBudget = budgets.create(user, new CreateBudgetCommand("Food", food, "MONTHLY", "1000", "INR", 80, "2026-11-01"));
        overall = budgets.create(user, new CreateBudgetCommand("All", null, "MONTHLY", "5000", "INR", 80, "2026-11-01"));
        budgets.create(user, new CreateBudgetCommand("Food USD", food, "MONTHLY", "10", "USD", 80, "2026-11-01"));
    }

    @Test
    void consumesMatchingCategoryParentAndOverallBudgetsInSameCurrency() {
        ConsumptionResult result = consume("txn:1:budget", dining, food, "300", NOV_10);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.statuses()).extracting(s -> s.budget().id())
                .containsExactlyInAnyOrder(foodBudget.id(), overall.id());
        assertThat(spent(foodBudget)).isEqualByComparingTo("300");
        assertThat(spent(overall)).isEqualByComparingTo("300");
    }

    @Test
    void ignoresExpensesBeforeStartDateAndInOtherPeriods() {
        consume("txn:2:budget", food, null, "100", LocalDate.parse("2026-10-31"));
        consume("txn:3:budget", food, null, "100", LocalDate.parse("2026-12-01"));

        assertThat(budgets.status(user, foodBudget.id(), "2026-11-15").spent()).isEqualTo(Money.zero("INR"));
        assertThat(budgets.status(user, foodBudget.id(), "2026-12-15").spent()).isEqualTo(Money.of("100", "INR"));
    }

    @Test
    void consumeIsIdempotentPerOperationKey() {
        consume("txn:4:budget", food, null, "100", NOV_10);
        ConsumptionResult replay = consume("txn:4:budget", food, null, "100", NOV_10);

        assertThat(replay.duplicate()).isTrue();
        assertThat(spent(foodBudget)).isEqualByComparingTo("100");
    }

    @Test
    void releaseReversesOnceAndIsIdempotent() {
        consume("txn:5:budget", food, null, "100", NOV_10);

        assertThat(consumption.release(user, "txn:5:budget")).isEqualTo(ReleaseOutcome.RELEASED);
        assertThat(consumption.release(user, "txn:5:budget")).isEqualTo(ReleaseOutcome.ALREADY_RELEASED);
        assertThat(spent(foodBudget)).isEqualByComparingTo("0");
        assertThat(spent(overall)).isEqualByComparingTo("0");
    }

    @Test
    void releaseBeforeConsumeBlocksTheLateConsume() {
        assertThat(consumption.release(user, "txn:6:budget")).isEqualTo(ReleaseOutcome.NOTHING_TO_RELEASE);

        assertThatThrownBy(() -> consume("txn:6:budget", food, null, "100", NOV_10))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(BudgetErrors.OPERATION_CANCELLED);
    }

    @Test
    void consumeMatchingNoBudgetIsStillIdempotentAndReleasable() {
        UUID uncategorised = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        ConsumptionResult first = consumption.consume(new ConsumeBudgetCommand(stranger, UUID.randomUUID(),
                uncategorised, null, Money.of("5", "INR"), NOV_10, "txn:7:budget"));

        assertThat(first.statuses()).isEmpty();
        assertThat(consumption.release(stranger, "txn:7:budget")).isEqualTo(ReleaseOutcome.RELEASED);
    }

    @Test
    void publishesThresholdAndExceededAlertsOncePerPeriod() {
        consume("txn:8:budget", food, null, "850", NOV_10);   // crosses 80%
        consume("txn:9:budget", food, null, "50", NOV_10);    // still between
        consume("txn:10:budget", food, null, "200", NOV_10);  // exceeds 1000
        consume("txn:11:budget", food, null, "10", NOV_10);   // already exceeded

        assertThat(events("budget.BudgetThresholdReached", foodBudget)).isEqualTo(1);
        assertThat(events("budget.BudgetExceeded", foodBudget)).isEqualTo(1);
        assertThat(budgets.status(user, foodBudget.id(), "2026-11-10").state()).isEqualTo(BudgetStatus.State.EXCEEDED);
    }

    @Test
    void concurrentConsumptionNeverLosesUpdates() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String key = "txn:conc-" + i + ":budget";
            tasks.add(() -> consume(key, food, null, "7.5", NOV_10));
            tasks.add(() -> consume(key, food, null, "7.5", NOV_10)); // duplicate delivery
        }
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Object> f : pool.invokeAll(tasks)) {
                f.get();
            }
        }

        assertThat(spent(foodBudget)).isEqualByComparingTo("150");
        assertThat(jdbc.sql("SELECT sum(amount) FROM budget_consumption WHERE budget_id = :id")
                .param("id", foodBudget.id()).query(java.math.BigDecimal.class).single()).isEqualByComparingTo("150");
    }

    private ConsumptionResult consume(String key, UUID category, UUID parent, String amount, LocalDate date) {
        return consumption.consume(new ConsumeBudgetCommand(user, UUID.randomUUID(), category, parent,
                Money.of(amount, "INR"), date, key));
    }

    private java.math.BigDecimal spent(Budget budget) {
        return budgets.status(user, budget.id(), NOV_10.toString()).spent().amount();
    }

    private long events(String type, Budget budget) {
        return jdbc.sql("SELECT count(*) FROM outbox_message WHERE message_type = :t AND aggregate_id = :id")
                .param("t", type).param("id", budget.id().toString()).query(Long.class).single();
    }
}
