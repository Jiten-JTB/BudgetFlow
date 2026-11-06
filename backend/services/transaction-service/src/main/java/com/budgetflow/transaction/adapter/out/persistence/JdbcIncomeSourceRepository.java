package com.budgetflow.transaction.adapter.out.persistence;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.application.port.IncomeSourceRepository;
import com.budgetflow.transaction.domain.IncomeSource;
import com.budgetflow.transaction.domain.IncomeSourceType;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcIncomeSourceRepository implements IncomeSourceRepository {

    private static final String COLUMNS = "id, user_id, name, type, default_account_id, notes, archived_at, created_at, updated_at, version";

    private final JdbcClient jdbc;

    public JdbcIncomeSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(IncomeSource s) {
        try {
            jdbc.sql("""
                    INSERT INTO income_source (%s)
                    VALUES (:id, :userId, :name, :type, :defaultAccountId, :notes, :archivedAt, :createdAt, :updatedAt, :version)
                    """.formatted(COLUMNS))
                    .param("id", s.id()).param("userId", s.userId()).param("name", s.name())
                    .param("type", s.type().name()).param("defaultAccountId", s.defaultAccountId())
                    .param("notes", s.notes()).param("archivedAt", Sql.ts(s.archivedAt()))
                    .param("createdAt", Sql.ts(s.createdAt())).param("updatedAt", Sql.ts(s.updatedAt()))
                    .param("version", s.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
    }

    @Override
    public void update(IncomeSource s) {
        int updated;
        try {
            updated = jdbc.sql("""
                    UPDATE income_source SET name = :name, type = :type, default_account_id = :defaultAccountId,
                           notes = :notes, archived_at = :archivedAt, updated_at = :updatedAt, version = version + 1
                    WHERE id = :id AND version = :version
                    """)
                    .param("id", s.id()).param("name", s.name()).param("type", s.type().name())
                    .param("defaultAccountId", s.defaultAccountId()).param("notes", s.notes())
                    .param("archivedAt", Sql.ts(s.archivedAt())).param("updatedAt", Sql.ts(s.updatedAt()))
                    .param("version", s.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Income source " + s.id() + " was modified concurrently");
        }
        s.incrementVersion();
    }

    @Override
    public Optional<IncomeSource> findOwned(UUID userId, UUID sourceId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM income_source WHERE id = :id AND user_id = :userId")
                .param("id", sourceId).param("userId", userId).query(JdbcIncomeSourceRepository::map).optional();
    }

    @Override
    public List<IncomeSource> list(UUID userId, boolean includeArchived) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM income_source WHERE user_id = :userId"
                        + (includeArchived ? "" : " AND archived_at IS NULL") + " ORDER BY lower(name)")
                .param("userId", userId).query(JdbcIncomeSourceRepository::map).list();
    }

    private static RuntimeException duplicate(DuplicateKeyException e) {
        return Sql.isUniqueViolationOf(e, "ux_income_source_user_active_name")
                ? new DomainException(TransactionErrors.DUPLICATE_NAME, "An income source with this name already exists")
                : e;
    }

    private static IncomeSource map(ResultSet rs, int row) throws SQLException {
        return IncomeSource.rehydrate(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("name"), IncomeSourceType.valueOf(rs.getString("type")),
                rs.getObject("default_account_id", UUID.class), rs.getString("notes"), Sql.instant(rs, "archived_at"),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"), rs.getLong("version"));
    }
}
