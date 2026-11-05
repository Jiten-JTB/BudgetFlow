package com.budgetflow.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.common.core.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class BudgetTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final LocalDate NOV_1 = LocalDate.parse("2026-11-01");

    @Test
    void validatesLimitThresholdAndName() {
        assertError(() -> budget("Food", "0", 80), BudgetErrors.INVALID_LIMIT);
        assertError(() -> budget("Food", "-1", 80), BudgetErrors.INVALID_LIMIT);
        assertError(() -> budget("Food", "100", 0), BudgetErrors.INVALID_THRESHOLD);
        assertError(() -> budget("Food", "100", 101), BudgetErrors.INVALID_THRESHOLD);
        assertError(() -> budget(" ", "100", 80), BudgetErrors.INVALID_NAME);
        assertThat(budget(" Food ", "100", 80).name()).isEqualTo("Food");
    }

    @Test
    void updateKeepsCurrencyAndRejectsArchived() {
        Budget b = budget("Food", "100", 80);
        b.update(null, Money.of("150", "INR"), 90, NOW);
        assertThat(b.limit()).isEqualTo(Money.of("150", "INR"));
        assertThat(b.alertThresholdPercent()).isEqualTo(90);
        assertError(() -> b.update(null, Money.of("150", "USD"), null, NOW), BudgetErrors.INVALID_LIMIT);

        b.archive(NOW);
        assertError(() -> b.update("X", null, null, NOW), BudgetErrors.BUDGET_ARCHIVED);
    }

    @Test
    void statusReportsRemainingPercentAndState() {
        Budget b = budget("Food", "1000", 80);
        PeriodRange nov = BudgetPeriod.MONTHLY.rangeContaining(NOV_1);

        assertThat(new BudgetStatus(b, nov, Money.of("500", "INR")).state()).isEqualTo(BudgetStatus.State.ON_TRACK);
        BudgetStatus warning = new BudgetStatus(b, nov, Money.of("800", "INR"));
        assertThat(warning.state()).isEqualTo(BudgetStatus.State.WARNING);
        assertThat(warning.percentUsed()).isEqualTo(80);
        assertThat(warning.remaining()).isEqualTo(Money.of("200", "INR"));
        BudgetStatus exceeded = new BudgetStatus(b, nov, Money.of("1250.50", "INR"));
        assertThat(exceeded.state()).isEqualTo(BudgetStatus.State.EXCEEDED);
        assertThat(exceeded.remaining()).isEqualTo(Money.of("-250.50", "INR"));
        assertThat(exceeded.percentUsed()).isEqualTo(125);
    }

    @Test
    void usageReportsEachCrossingOncePerPeriod() {
        Budget b = budget("Food", "1000", 80);
        BudgetUsage usage = BudgetUsage.start(b, BudgetPeriod.MONTHLY.rangeContaining(NOV_1), NOW);

        assertThat(usage.add(Money.of("700", "INR"), b, NOW)).isEqualTo(new UsageChange(false, false));
        assertThat(usage.add(Money.of("150", "INR"), b, NOW)).isEqualTo(new UsageChange(true, false));
        assertThat(usage.add(Money.of("10", "INR"), b, NOW)).isEqualTo(new UsageChange(false, false));
        assertThat(usage.add(Money.of("200", "INR"), b, NOW)).isEqualTo(new UsageChange(false, true));
        assertThat(usage.add(Money.of("-500", "INR"), b, NOW)).as("releases never alert").isEqualTo(new UsageChange(false, false));
        assertThat(usage.add(Money.of("500", "INR"), b, NOW)).as("already notified this period").isEqualTo(new UsageChange(false, false));
        assertThat(usage.spent()).isEqualTo(Money.of("1060", "INR"));
    }

    @Test
    void singleLargeExpenseCrossesThresholdAndLimitTogether() {
        Budget b = budget("Food", "1000", 80);
        BudgetUsage usage = BudgetUsage.start(b, BudgetPeriod.MONTHLY.rangeContaining(NOV_1), NOW);

        assertThat(usage.add(Money.of("1500", "INR"), b, NOW)).isEqualTo(new UsageChange(true, true));
    }

    @Test
    void appliesOnlyFromStartDateInSameCurrency() {
        Budget b = budget("Food", "1000", 80);

        assertThat(b.appliesTo(NOV_1, Money.of("1", "INR").currency())).isTrue();
        assertThat(b.appliesTo(NOV_1.minusDays(1), Money.of("1", "INR").currency())).isFalse();
        assertThat(b.appliesTo(NOV_1, Money.of("1", "USD").currency())).isFalse();
    }

    private static Budget budget(String name, String limit, int threshold) {
        return Budget.create(UUID.randomUUID(), UUID.randomUUID(), name, UUID.randomUUID(), BudgetPeriod.MONTHLY,
                Money.of(limit, "INR"), threshold, NOV_1, NOW);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
