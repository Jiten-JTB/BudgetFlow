package com.budgetflow.budget.adapter.out.persistence;

import com.budgetflow.budget.application.port.BudgetRepository;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetErrors;
import com.budgetflow.budget.domain.BudgetPeriod;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcBudgetRepository implements BudgetRepository {

    private static final String COLUMNS = """
            id, user_id, name, category_id, period, limit_amount, currency, alert_threshold_percent, starts_on,
            archived_at, created_at, updated_at, version""";

    private final JdbcClient jdbc;

    public JdbcBudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Budget b) {
        try {
            jdbc.sql("""
                    INSERT INTO budget (%s)
                    VALUES (:id, :userId, :name, :categoryId, :period, :limit, :currency, :threshold, :startsOn,
                            :archivedAt, :createdAt, :updatedAt, :version)
                    """.formatted(COLUMNS))
                    .param("id", b.id()).param("userId", b.userId()).param("name", b.name())
                    .param("categoryId", b.categoryId()).param("period", b.period().name())
                    .param("limit", b.limit().amount()).param("currency", b.currency().getCurrencyCode())
                    .param("threshold", b.alertThresholdPercent()).param("startsOn", b.startsOn())
                    .param("archivedAt", Sql.ts(b.archivedAt())).param("createdAt", Sql.ts(b.createdAt()))
                    .param("updatedAt", Sql.ts(b.updatedAt())).param("version", b.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw Sql.isUniqueViolationOf(e, "ux_budget_user_active_scope")
                    ? new DomainException(BudgetErrors.DUPLICATE_BUDGET,
                            "An active budget already exists for this category, period and currency")
                    : e;
        }
    }

    @Override
    public void update(Budget b) {
        int updated = jdbc.sql("""
                UPDATE budget SET name = :name, limit_amount = :limit, alert_threshold_percent = :threshold,
                       archived_at = :archivedAt, updated_at = :updatedAt, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", b.id()).param("name", b.name()).param("limit", b.limit().amount())
                .param("threshold", b.alertThresholdPercent()).param("archivedAt", Sql.ts(b.archivedAt()))
                .param("updatedAt", Sql.ts(b.updatedAt())).param("version", b.version())
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Budget " + b.id() + " was modified concurrently");
        }
        b.incrementVersion();
    }

    @Override
    public Optional<Budget> findOwned(UUID userId, UUID budgetId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM budget WHERE id = :id AND user_id = :userId")
                .param("id", budgetId).param("userId", userId).query(JdbcBudgetRepository::map).optional();
    }

    @Override
    public List<Budget> list(UUID userId, boolean includeArchived) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM budget WHERE user_id = :userId"
                        + (includeArchived ? "" : " AND archived_at IS NULL") + " ORDER BY created_at, id")
                .param("userId", userId).query(JdbcBudgetRepository::map).list();
    }

    @Override
    public List<Budget> findMatching(UUID userId, Currency currency, LocalDate date, Collection<UUID> categoryIds) {
        String scope = categoryIds.isEmpty() ? "category_id IS NULL" : "(category_id IS NULL OR category_id IN (:categoryIds))";
        var spec = jdbc.sql("SELECT " + COLUMNS + """
                         FROM budget
                        WHERE user_id = :userId AND archived_at IS NULL AND currency = :currency AND starts_on <= :date
                          AND """ + " " + scope + " ORDER BY id")
                .param("userId", userId).param("currency", currency.getCurrencyCode()).param("date", date);
        if (!categoryIds.isEmpty()) {
            spec = spec.param("categoryIds", categoryIds);
        }
        return spec.query(JdbcBudgetRepository::map).list();
    }

    private static Budget map(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return Budget.rehydrate(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("name"), rs.getObject("category_id", UUID.class), BudgetPeriod.valueOf(rs.getString("period")),
                Money.of(rs.getBigDecimal("limit_amount"), currency), rs.getInt("alert_threshold_percent"),
                rs.getObject("starts_on", LocalDate.class), Sql.instant(rs, "archived_at"),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"), rs.getLong("version"));
    }
}
