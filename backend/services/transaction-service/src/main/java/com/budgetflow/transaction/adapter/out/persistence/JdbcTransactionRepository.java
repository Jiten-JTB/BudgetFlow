package com.budgetflow.transaction.adapter.out.persistence;

import com.budgetflow.common.core.money.Money;
import com.budgetflow.transaction.application.TransactionCursor;
import com.budgetflow.transaction.application.TransactionQuery;
import com.budgetflow.transaction.application.port.TransactionRepository;
import com.budgetflow.transaction.domain.Transaction;
import com.budgetflow.transaction.domain.TransactionKind;
import com.budgetflow.transaction.domain.TransactionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcTransactionRepository implements TransactionRepository {

    private static final String COLUMNS = """
            id, user_id, kind, status, account_id, counter_account_id, amount, currency, category_id, income_source_id,
            occurred_on, description, payee, idempotency_key, request_hash, saga_id, failure_code, failure_reason,
            created_at, updated_at, completed_at, version""";

    private final JdbcClient jdbc;

    public JdbcTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(Transaction t) {
        return jdbc.sql("""
                INSERT INTO money_transaction (%s)
                VALUES (:id, :userId, :kind, :status, :accountId, :counterAccountId, :amount, :currency, :categoryId,
                        :incomeSourceId, :occurredOn, :description, :payee, :idempotencyKey, :requestHash, :sagaId,
                        :failureCode, :failureReason, :createdAt, :updatedAt, :completedAt, :version)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """.formatted(COLUMNS))
                .param("id", t.id()).param("userId", t.userId()).param("kind", t.kind().name())
                .param("status", t.status().name()).param("accountId", t.accountId())
                .param("counterAccountId", t.counterAccountId()).param("amount", t.amount().amount())
                .param("currency", t.amount().currency().getCurrencyCode()).param("categoryId", t.categoryId())
                .param("incomeSourceId", t.incomeSourceId()).param("occurredOn", t.occurredOn())
                .param("description", t.description()).param("payee", t.payee())
                .param("idempotencyKey", t.idempotencyKey()).param("requestHash", t.requestHash())
                .param("sagaId", t.sagaId()).param("failureCode", t.failureCode())
                .param("failureReason", t.failureReason()).param("createdAt", Sql.ts(t.createdAt()))
                .param("updatedAt", Sql.ts(t.updatedAt())).param("completedAt", Sql.ts(t.completedAt()))
                .param("version", t.version())
                .update() == 1;
    }

    @Override
    public void update(Transaction t) {
        int updated = jdbc.sql("""
                UPDATE money_transaction SET status = :status, saga_id = :sagaId, failure_code = :failureCode,
                       failure_reason = :failureReason, updated_at = :updatedAt, completed_at = :completedAt,
                       version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", t.id()).param("status", t.status().name()).param("sagaId", t.sagaId())
                .param("failureCode", t.failureCode()).param("failureReason", t.failureReason())
                .param("updatedAt", Sql.ts(t.updatedAt())).param("completedAt", Sql.ts(t.completedAt()))
                .param("version", t.version())
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Transaction " + t.id() + " was modified concurrently");
        }
        t.incrementVersion();
    }

    @Override
    public Optional<Transaction> findOwned(UUID userId, UUID transactionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM money_transaction WHERE id = :id AND user_id = :userId")
                .param("id", transactionId).param("userId", userId).query(JdbcTransactionRepository::map).optional();
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM money_transaction WHERE user_id = :userId AND idempotency_key = :key")
                .param("userId", userId).param("key", idempotencyKey).query(JdbcTransactionRepository::map).optional();
    }

    @Override
    public List<Transaction> search(UUID userId, TransactionQuery q, TransactionCursor after, int limit) {
        List<String> where = new ArrayList<>(List.of("user_id = :userId"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        filter(where, params, q.from(), "occurred_on >= :from", "from");
        filter(where, params, q.to(), "occurred_on <= :to", "to");
        filter(where, params, q.kind() == null ? null : q.kind().name(), "kind = :kind", "kind");
        filter(where, params, q.status() == null ? null : q.status().name(), "status = :status", "status");
        filter(where, params, q.accountId(), "(account_id = :accountId OR counter_account_id = :accountId)", "accountId");
        filter(where, params, q.categoryId(), "category_id = :categoryId", "categoryId");
        if (after != null) {
            where.add("(occurred_on, id) < (:afterDate, :afterId)");
            params.put("afterDate", after.occurredOn());
            params.put("afterId", after.id());
        }
        params.put("limit", limit);
        return jdbc.sql("SELECT " + COLUMNS + " FROM money_transaction WHERE " + String.join(" AND ", where)
                        + " ORDER BY occurred_on DESC, id DESC LIMIT :limit")
                .params(params).query(JdbcTransactionRepository::map).list();
    }

    private static void filter(List<String> where, Map<String, Object> params, Object value, String clause, String name) {
        if (value != null) {
            where.add(clause);
            params.put(name, value);
        }
    }

    private static Transaction map(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return Transaction.rehydrate(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                TransactionKind.valueOf(rs.getString("kind")), TransactionStatus.valueOf(rs.getString("status")),
                rs.getObject("account_id", UUID.class), rs.getObject("counter_account_id", UUID.class),
                Money.of(rs.getBigDecimal("amount"), currency), rs.getObject("category_id", UUID.class),
                rs.getObject("income_source_id", UUID.class), rs.getObject("occurred_on", LocalDate.class),
                rs.getString("description"), rs.getString("payee"), rs.getString("idempotency_key"),
                rs.getString("request_hash"), rs.getObject("saga_id", UUID.class), rs.getString("failure_code"),
                rs.getString("failure_reason"), Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"),
                Sql.instant(rs, "completed_at"), rs.getLong("version"));
    }
}
