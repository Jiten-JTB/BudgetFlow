package com.budgetflow.account.adapter.out.persistence;

import com.budgetflow.account.application.port.OperationRegistry;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcOperationRegistry implements OperationRegistry {

    private final JdbcClient jdbc;

    public JdbcOperationRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Postgres advisory lock scoped to the current transaction; keys hash to 64 bits. */
    @Override
    public void lock(String operationKey) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", operationKey)
                .query().listOfRows();
    }

    @Override
    public boolean isCancelled(String operationKey) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM operation_tombstone WHERE operation_key = :key)")
                .param("key", operationKey).query(Boolean.class).single();
    }

    @Override
    public void cancel(String operationKey, UUID userId, Instant now) {
        jdbc.sql("""
                INSERT INTO operation_tombstone (operation_key, user_id, created_at) VALUES (:key, :userId, :now)
                ON CONFLICT DO NOTHING""")
                .param("key", operationKey).param("userId", userId).param("now", Sql.ts(now))
                .update();
    }
}
