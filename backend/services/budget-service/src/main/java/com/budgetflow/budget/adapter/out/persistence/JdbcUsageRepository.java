package com.budgetflow.budget.adapter.out.persistence;

import com.budgetflow.budget.application.port.UsageRepository;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetUsage;
import com.budgetflow.budget.domain.PeriodRange;
import com.budgetflow.common.core.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcUsageRepository implements UsageRepository {

    private static final String SELECT = """
            SELECT budget_id, period_start, period_end, spent, threshold_notified_at, exceeded_notified_at, updated_at
            FROM budget_period_usage WHERE budget_id = :budgetId AND period_start = :start""";

    private final JdbcClient jdbc;

    public JdbcUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BudgetUsage lockOrCreate(Budget budget, PeriodRange period, Instant now) {
        jdbc.sql("""
                INSERT INTO budget_period_usage (budget_id, period_start, period_end, spent, updated_at)
                VALUES (:budgetId, :start, :end, 0, :now)
                ON CONFLICT (budget_id, period_start) DO NOTHING""")
                .param("budgetId", budget.id()).param("start", period.start()).param("end", period.end())
                .param("now", Sql.ts(now))
                .update();
        return jdbc.sql(SELECT + " FOR UPDATE")
                .param("budgetId", budget.id()).param("start", period.start())
                .query((rs, row) -> map(rs, budget)).single();
    }

    @Override
    public Optional<BudgetUsage> find(Budget budget, PeriodRange period) {
        return jdbc.sql(SELECT).param("budgetId", budget.id()).param("start", period.start())
                .query((rs, row) -> map(rs, budget)).optional();
    }

    @Override
    public void update(BudgetUsage u) {
        jdbc.sql("""
                UPDATE budget_period_usage SET spent = :spent, threshold_notified_at = :threshold,
                       exceeded_notified_at = :exceeded, updated_at = :updatedAt
                WHERE budget_id = :budgetId AND period_start = :start""")
                .param("budgetId", u.budgetId()).param("start", u.period().start()).param("spent", u.spent().amount())
                .param("threshold", Sql.ts(u.thresholdNotifiedAt())).param("exceeded", Sql.ts(u.exceededNotifiedAt()))
                .param("updatedAt", Sql.ts(u.updatedAt()))
                .update();
    }

    private static BudgetUsage map(ResultSet rs, Budget budget) throws SQLException {
        return BudgetUsage.rehydrate(rs.getObject("budget_id", UUID.class),
                new PeriodRange(rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class)),
                Money.of(rs.getBigDecimal("spent"), budget.currency()), Sql.instant(rs, "threshold_notified_at"),
                Sql.instant(rs, "exceeded_notified_at"), Sql.instant(rs, "updated_at"));
    }
}
