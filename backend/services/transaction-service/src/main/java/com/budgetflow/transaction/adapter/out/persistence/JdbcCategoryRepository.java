package com.budgetflow.transaction.adapter.out.persistence;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.application.port.CategoryRepository;
import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.CategoryKind;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcCategoryRepository implements CategoryRepository {

    private static final String COLUMNS = "id, user_id, name, kind, parent_id, icon, color, archived_at, created_at, updated_at, version";

    private final JdbcClient jdbc;

    public JdbcCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Category c) {
        try {
            jdbc.sql("""
                    INSERT INTO category (%s)
                    VALUES (:id, :userId, :name, :kind, :parentId, :icon, :color, :archivedAt, :createdAt, :updatedAt, :version)
                    """.formatted(COLUMNS))
                    .param("id", c.id()).param("userId", c.userId()).param("name", c.name())
                    .param("kind", c.kind().name()).param("parentId", c.parentId()).param("icon", c.icon())
                    .param("color", c.color()).param("archivedAt", Sql.ts(c.archivedAt()))
                    .param("createdAt", Sql.ts(c.createdAt())).param("updatedAt", Sql.ts(c.updatedAt()))
                    .param("version", c.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
    }

    @Override
    public void update(Category c) {
        int updated;
        try {
            updated = jdbc.sql("""
                    UPDATE category SET name = :name, icon = :icon, color = :color, archived_at = :archivedAt,
                           updated_at = :updatedAt, version = version + 1
                    WHERE id = :id AND version = :version
                    """)
                    .param("id", c.id()).param("name", c.name()).param("icon", c.icon()).param("color", c.color())
                    .param("archivedAt", Sql.ts(c.archivedAt())).param("updatedAt", Sql.ts(c.updatedAt()))
                    .param("version", c.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Category " + c.id() + " was modified concurrently");
        }
        c.incrementVersion();
    }

    @Override
    public Optional<Category> findOwned(UUID userId, UUID categoryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM category WHERE id = :id AND user_id = :userId")
                .param("id", categoryId).param("userId", userId).query(JdbcCategoryRepository::map).optional();
    }

    @Override
    public List<Category> list(UUID userId, CategoryKind kind, boolean includeArchived) {
        String sql = "SELECT " + COLUMNS + " FROM category WHERE user_id = :userId"
                + (kind == null ? "" : " AND kind = :kind")
                + (includeArchived ? "" : " AND archived_at IS NULL")
                + " ORDER BY kind, parent_id NULLS FIRST, lower(name)";
        var spec = jdbc.sql(sql).param("userId", userId);
        if (kind != null) {
            spec = spec.param("kind", kind.name());
        }
        return spec.query(JdbcCategoryRepository::map).list();
    }

    @Override
    public boolean existsForUser(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM category WHERE user_id = :userId)")
                .param("userId", userId).query(Boolean.class).single();
    }

    private static RuntimeException duplicate(DuplicateKeyException e) {
        return Sql.isUniqueViolationOf(e, "ux_category_user_active_name")
                ? new DomainException(TransactionErrors.DUPLICATE_NAME, "A category with this name already exists here")
                : e;
    }

    private static Category map(ResultSet rs, int row) throws SQLException {
        return Category.rehydrate(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("name"), CategoryKind.valueOf(rs.getString("kind")), rs.getObject("parent_id", UUID.class),
                rs.getString("icon"), rs.getString("color"), Sql.instant(rs, "archived_at"),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"), rs.getLong("version"));
    }
}
