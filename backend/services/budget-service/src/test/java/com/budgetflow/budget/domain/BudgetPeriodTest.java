package com.budgetflow.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BudgetPeriodTest {

    @ParameterizedTest(name = "{0} {1} -> {2}..{3}")
    @CsvSource({
        "WEEKLY,  2026-11-04, 2026-11-02, 2026-11-08",   // Wednesday -> ISO week Mon..Sun
        "WEEKLY,  2026-11-02, 2026-11-02, 2026-11-08",   // Monday
        "WEEKLY,  2026-11-08, 2026-11-02, 2026-11-08",   // Sunday
        "WEEKLY,  2027-01-01, 2026-12-28, 2027-01-03",   // week spanning new year
        "MONTHLY, 2026-11-30, 2026-11-01, 2026-11-30",
        "MONTHLY, 2028-02-15, 2028-02-01, 2028-02-29",   // leap year
        "MONTHLY, 2027-02-15, 2027-02-01, 2027-02-28",
        "YEARLY,  2026-07-01, 2026-01-01, 2026-12-31"
    })
    void computesCalendarPeriodContainingDate(BudgetPeriod period, LocalDate date, LocalDate start, LocalDate end) {
        assertThat(period.rangeContaining(date)).isEqualTo(new PeriodRange(start, end));
    }
}
