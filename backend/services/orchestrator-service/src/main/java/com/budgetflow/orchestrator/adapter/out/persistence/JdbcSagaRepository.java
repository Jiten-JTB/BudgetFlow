package com.budgetflow.orchestrator.adapter.out.persistence;

import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.orchestrator.application.port.SagaRepository;
import com.budgetflow.orchestrator.domain.SagaEvent;
import com.budgetflow.orchestrator.domain.SagaInstance;
import com.budgetflow.orchestrator.domain.SagaPhase;
import com.budgetflow.orchestrator.domain.SagaStatus;
import com.budgetflow.orchestrator.domain.SagaType;
import com.budgetflow.orchestrator.domain.TransactionSagaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

public class JdbcSagaRepository implements SagaRepository {

    private static final String COLUMNS = """
            id, saga_type, business_key, user_id, data::text AS data, status, phase, step_index, awaiting_command_id,
            awaiting_command_type, attempts, deadline_at, failure_code, failure_reason, created_at, updated_at,
            finished_at, version""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcSagaRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insertIfAbsent(SagaInstance s) {
        int inserted = jdbc.sql("""
                INSERT INTO saga_instance (id, saga_type, business_key, user_id, data, status, phase, step_index,
                    awaiting_command_id, awaiting_command_type, attempts, deadline_at, failure_code, failure_reason,
                    created_at, updated_at, finished_at, version)
                VALUES (:id, :type, :businessKey, :userId, CAST(:data AS JSONB), :status, :phase, :stepIndex,
                    :awaitingId, :awaitingType, :attempts, :deadline, :failureCode, :failureReason,
                    :createdAt, :updatedAt, :finishedAt, :version)
                ON CONFLICT (saga_type, business_key) DO NOTHING
                """)
                .param("id", s.id()).param("type", s.type().name())
                .param("businessKey", s.data().transactionId().toString()).param("userId", s.data().userId())
                .param("data", json.writeValueAsString(s.data())).param("status", s.status().name())
                .param("phase", s.phase().name()).param("stepIndex", s.stepIndex())
                .param("awaitingId", s.awaitingCommandId()).param("awaitingType", s.awaitingCommandType())
                .param("attempts", s.attempts()).param("deadline", ts(s.deadlineAt()))
                .param("failureCode", s.failureCode()).param("failureReason", s.failureReason())
                .param("createdAt", ts(s.createdAt())).param("updatedAt", ts(s.updatedAt()))
                .param("finishedAt", ts(s.finishedAt())).param("version", s.version())
                .update();
        if (inserted == 1) {
            writeEvents(s);
        }
        return inserted == 1;
    }

    @Override
    public void update(SagaInstance s) {
        int updated = jdbc.sql("""
                UPDATE saga_instance SET status = :status, phase = :phase, step_index = :stepIndex,
                       awaiting_command_id = :awaitingId, awaiting_command_type = :awaitingType, attempts = :attempts,
                       deadline_at = :deadline, failure_code = :failureCode, failure_reason = :failureReason,
                       updated_at = :updatedAt, finished_at = :finishedAt, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", s.id()).param("status", s.status().name()).param("phase", s.phase().name())
                .param("stepIndex", s.stepIndex()).param("awaitingId", s.awaitingCommandId())
                .param("awaitingType", s.awaitingCommandType()).param("attempts", s.attempts())
                .param("deadline", ts(s.deadlineAt())).param("failureCode", s.failureCode())
                .param("failureReason", s.failureReason()).param("updatedAt", ts(s.updatedAt()))
                .param("finishedAt", ts(s.finishedAt())).param("version", s.version())
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Saga " + s.id() + " was modified concurrently");
        }
        s.incrementVersion();
        writeEvents(s);
    }

    @Override
    public Optional<SagaInstance> findForUpdate(UUID sagaId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM saga_instance WHERE id = :id FOR UPDATE")
                .param("id", sagaId).query(this::map).optional();
    }

    @Override
    public List<SagaInstance> lockDue(Instant now, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM saga_instance
                        WHERE status IN ('RUNNING', 'COMPENSATING') AND deadline_at <= :now
                        ORDER BY deadline_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED""")
                .param("now", ts(now)).param("limit", limit).query(this::map).list();
    }

    private void writeEvents(SagaInstance s) {
        for (SagaEvent e : s.drainEvents()) {
            jdbc.sql("""
                    INSERT INTO saga_event (id, saga_id, event_type, command_type, command_id, detail, occurred_at)
                    VALUES (:id, :sagaId, :type, :commandType, :commandId, :detail, :occurredAt)""")
                    .param("id", UuidV7.generate()).param("sagaId", s.id()).param("type", e.type().name())
                    .param("commandType", e.commandType()).param("commandId", e.commandId())
                    .param("detail", e.detail()).param("occurredAt", ts(e.occurredAt()))
                    .update();
        }
    }

    private SagaInstance map(ResultSet rs, int row) throws SQLException {
        return SagaInstance.rehydrate(rs.getObject("id", UUID.class), SagaType.valueOf(rs.getString("saga_type")),
                json.readValue(rs.getString("data"), TransactionSagaData.class),
                SagaStatus.valueOf(rs.getString("status")), SagaPhase.valueOf(rs.getString("phase")),
                rs.getInt("step_index"), rs.getObject("awaiting_command_id", UUID.class), rs.getInt("attempts"),
                instant(rs, "deadline_at"), rs.getString("failure_code"), rs.getString("failure_reason"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "finished_at"), rs.getLong("version"));
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
