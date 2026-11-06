package com.budgetflow.budget.adapter.out.persistence;

import com.budgetflow.budget.application.port.ConsumptionRepository;
import com.budgetflow.budget.domain.PeriodRange;
import com.budgetflow.common.core.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcConsumptionRepository implements ConsumptionRepository {

    private final JdbcClient jdbc;

    public JdbcConsumptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Consumption c) {
        jdbc.sql("""
                INSERT INTO budget_consumption (id, budget_id, user_id, period_start, transaction_id, operation_key,
                                                amount, reverses_consumption_id, created_at)
                VALUES (:id, :budgetId, :userId, :start, :transactionId, :key, :amount, :reverses, :createdAt)""")
                .param("id", c.id()).param("budgetId", c.budgetId()).param("userId", c.userId())
                .param("start", c.period().start()).param("transactionId", c.transactionId())
                .param("key", c.operationKey()).param("amount", c.amount().amount())
                .param("reverses", c.reversesConsumptionId()).param("createdAt", Sql.ts(c.createdAt()))
                .update();
    }

    @Override
    public List<Consumption> findOriginals(String operationKey) {
        return jdbc.sql("""
                SELECT c.id, c.budget_id, c.user_id, c.period_start, u.period_end, c.transaction_id, c.operation_key,
                       c.amount, b.currency, c.created_at
                FROM budget_consumption c
                JOIN budget b ON b.id = c.budget_id
                JOIN budget_period_usage u ON u.budget_id = c.budget_id AND u.period_start = c.period_start
                WHERE c.operation_key = :key AND c.reverses_consumption_id IS NULL
                ORDER BY c.budget_id""")
                .param("key", operationKey)
                .query((rs, row) -> new Consumption(rs.getObject("id", UUID.class), rs.getObject("budget_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        new PeriodRange(rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class)),
                        rs.getObject("transaction_id", UUID.class), rs.getString("operation_key"),
                        Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency"))), null,
                        Sql.instant(rs, "created_at")))
                .list();
    }
}
