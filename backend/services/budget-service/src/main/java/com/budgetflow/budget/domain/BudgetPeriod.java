package com.budgetflow.budget.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Calendar periods. Weeks are ISO weeks (Monday to Sunday). Must match ck_budget_period in db/schema.sql. */
public enum BudgetPeriod {
    WEEKLY {
        @Override
        public PeriodRange rangeContaining(LocalDate date) {
            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new PeriodRange(monday, monday.plusDays(6));
        }
    },
    MONTHLY {
        @Override
        public PeriodRange rangeContaining(LocalDate date) {
            return new PeriodRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
        }
    },
    YEARLY {
        @Override
        public PeriodRange rangeContaining(LocalDate date) {
            return new PeriodRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()));
        }
    };

    public abstract PeriodRange rangeContaining(LocalDate date);
}
