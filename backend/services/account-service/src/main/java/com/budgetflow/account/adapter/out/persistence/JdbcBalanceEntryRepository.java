package com.budgetflow.account.adapter.out.persistence;

import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcBalanceEntryRepository implements BalanceEntryRepository {

    // Currency lives on the account; entries inherit it.
    private static final String SELECT = """
            SELECT e.id, e.account_id, e.user_id, e.operation_key, e.kind, e.amount, e.balance_after, e.reference_id,
                   e.reverses_entry_id, e.description, e.created_at, a.currency
            FROM balance_entry e JOIN account a ON a.id = e.account_id
            """;

    private final JdbcClient jdbc;

    public JdbcBalanceEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(BalanceEntry e) {
        jdbc.sql("""
                INSERT INTO balance_entry (id, account_id, user_id, operation_key, kind, amount, balance_after,
                                           reference_id, reverses_entry_id, description, created_at)
                VALUES (:id, :accountId, :userId, :operationKey, :kind, :amount, :balanceAfter, :referenceId,
                        :reversesEntryId, :description, :createdAt)
                """)
                .param("id", e.id()).param("accountId", e.accountId()).param("userId", e.userId())
                .param("operationKey", e.operationKey()).param("kind", e.kind().name())
                .param("amount", e.amount().amount()).param("balanceAfter", e.balanceAfter().amount())
                .param("referenceId", e.referenceId()).param("reversesEntryId", e.reversesEntryId())
                .param("description", e.description()).param("createdAt", Sql.ts(e.createdAt()))
                .update();
    }

    @Override
    public Optional<BalanceEntry> findByOperationKey(String operationKey) {
        return jdbc.sql(SELECT + "WHERE e.operation_key = :key").param("key", operationKey)
                .query(JdbcBalanceEntryRepository::map).optional();
    }

    @Override
    public Optional<BalanceEntry> findReversalOf(UUID entryId) {
        return jdbc.sql(SELECT + "WHERE e.reverses_entry_id = :id").param("id", entryId)
                .query(JdbcBalanceEntryRepository::map).optional();
    }

    @Override
    public List<BalanceEntry> page(UUID accountId, UUID before, int limit) {
        String where = before == null ? "WHERE e.account_id = :accountId " : "WHERE e.account_id = :accountId AND e.id < :before ";
        var spec = jdbc.sql(SELECT + where + "ORDER BY e.id DESC LIMIT :limit")
                .param("accountId", accountId).param("limit", limit);
        if (before != null) {
            spec = spec.param("before", before);
        }
        return spec.query(JdbcBalanceEntryRepository::map).list();
    }

    private static BalanceEntry map(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new BalanceEntry(
                rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("operation_key"),
                EntryKind.valueOf(rs.getString("kind")), Money.of(rs.getBigDecimal("amount"), currency),
                Money.of(rs.getBigDecimal("balance_after"), currency), rs.getObject("reference_id", UUID.class),
                rs.getObject("reverses_entry_id", UUID.class), rs.getString("description"),
                Sql.instant(rs, "created_at"));
    }
}
