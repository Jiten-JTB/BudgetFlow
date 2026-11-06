package com.budgetflow.budget.adapter.out.persistence;

import com.budgetflow.budget.application.port.OperationLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcOperationLog implements OperationLog {

    private final JdbcClient jdbc;

    public JdbcOperationLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Postgres advisory lock scoped to the current transaction; keys hash to 64 bits. */
    @Override
    public void lock(String operationKey) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", operationKey)
                .query().listOfRows();
    }

    @Override
    public Optional<Status> find(String operationKey) {
        return jdbc.sql("SELECT status FROM budget_operation WHERE operation_key = :key").param("key", operationKey)
                .query(String.class).optional().map(Status::valueOf);
    }

    @Override
    public void record(String operationKey, UUID userId, Status status, Instant now) {
        jdbc.sql("""
                INSERT INTO budget_operation (operation_key, user_id, status, created_at, updated_at)
                VALUES (:key, :userId, :status, :now, :now)
                ON CONFLICT (operation_key) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at""")
                .param("key", operationKey).param("userId", userId).param("status", status.name())
                .param("now", Sql.ts(now))
                .update();
    }
}
