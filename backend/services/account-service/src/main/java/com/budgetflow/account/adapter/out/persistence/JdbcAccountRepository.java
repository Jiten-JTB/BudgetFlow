package com.budgetflow.account.adapter.out.persistence;

import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.AccountStatus;
import com.budgetflow.account.domain.AccountType;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcAccountRepository implements AccountRepository {

    private static final String COLUMNS = "id, user_id, name, type, currency, balance, status, created_at, updated_at, version";
    private static final String UNIQUE_NAME_INDEX = "ux_account_user_active_name";

    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account a) {
        try {
            jdbc.sql("""
                    INSERT INTO account (%s)
                    VALUES (:id, :userId, :name, :type, :currency, :balance, :status, :createdAt, :updatedAt, :version)
                    """.formatted(COLUMNS))
                    .param("id", a.id()).param("userId", a.userId()).param("name", a.name())
                    .param("type", a.type().name()).param("currency", a.currency().getCurrencyCode())
                    .param("balance", a.balance().amount()).param("status", a.status().name())
                    .param("createdAt", Sql.ts(a.createdAt())).param("updatedAt", Sql.ts(a.updatedAt()))
                    .param("version", a.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
    }

    @Override
    public void update(Account a) {
        int updated;
        try {
            updated = jdbc.sql("""
                    UPDATE account SET name = :name, balance = :balance, status = :status, updated_at = :updatedAt,
                           version = version + 1
                    WHERE id = :id AND version = :version
                    """)
                    .param("id", a.id()).param("name", a.name()).param("balance", a.balance().amount())
                    .param("status", a.status().name()).param("updatedAt", Sql.ts(a.updatedAt()))
                    .param("version", a.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw translate(e);
        }
        if (updated == 0) {
            throw new OptimisticLockingFailureException("Account " + a.id() + " was modified concurrently");
        }
        a.incrementVersion();
    }

    @Override
    public Optional<Account> findOwned(UUID userId, UUID accountId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM account WHERE id = :id AND user_id = :userId")
                .param("id", accountId).param("userId", userId).query(JdbcAccountRepository::map).optional();
    }

    @Override
    public Optional<Account> findOwnedForUpdate(UUID userId, UUID accountId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM account WHERE id = :id AND user_id = :userId FOR UPDATE")
                .param("id", accountId).param("userId", userId).query(JdbcAccountRepository::map).optional();
    }

    @Override
    public List<Account> listByUser(UUID userId, boolean includeArchived) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM account WHERE user_id = :userId"
                        + (includeArchived ? "" : " AND status = 'ACTIVE'") + " ORDER BY created_at, id")
                .param("userId", userId).query(JdbcAccountRepository::map).list();
    }

    @Override
    public boolean existsForUser(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE user_id = :userId)")
                .param("userId", userId).query(Boolean.class).single();
    }

    private static RuntimeException translate(DuplicateKeyException e) {
        if (String.valueOf(e.getMessage()).contains(UNIQUE_NAME_INDEX)) {
            return new DomainException(AccountErrors.DUPLICATE_ACCOUNT_NAME, "An active account with this name already exists");
        }
        return e;
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return Account.rehydrate(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("name"),
                AccountType.valueOf(rs.getString("type")), currency,
                Money.of(rs.getBigDecimal("balance"), currency), AccountStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"), rs.getLong("version"));
    }
}
