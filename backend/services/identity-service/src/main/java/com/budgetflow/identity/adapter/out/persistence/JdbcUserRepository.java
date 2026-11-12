package com.budgetflow.identity.adapter.out.persistence;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.identity.application.port.UserRepository;
import com.budgetflow.identity.domain.Email;
import com.budgetflow.identity.domain.IdentityErrors;
import com.budgetflow.identity.domain.User;
import com.budgetflow.identity.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = """
            id, email, password_hash, display_name, base_currency, timezone, status, email_verified_at,
            failed_login_attempts, locked_until, last_login_at, created_at, updated_at, version""";

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id")
                .param("id", id).query(JdbcUserRepository::map).optional();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = :email")
                .param("email", email.value()).query(JdbcUserRepository::map).optional();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)")
                .param("email", email.value()).query(Boolean.class).single();
    }

    @Override
    public void insert(User user) {
        try {
            jdbc.sql("""
                    INSERT INTO users (%s)
                    VALUES (:id, :email, :passwordHash, :displayName, :baseCurrency, :timezone, :status,
                            :emailVerifiedAt, :failedLoginAttempts, :lockedUntil, :lastLoginAt, :createdAt, :updatedAt, :version)
                    """.formatted(COLUMNS))
                    .param("id", user.id())
                    .param("email", user.email().value())
                    .param("passwordHash", user.passwordHash())
                    .param("displayName", user.displayName())
                    .param("baseCurrency", user.baseCurrency().getCurrencyCode())
                    .param("timezone", user.timezone().getId())
                    .param("status", user.status().name())
                    .param("emailVerifiedAt", toDb(user.emailVerifiedAt()))
                    .param("failedLoginAttempts", user.failedLoginAttempts())
                    .param("lockedUntil", toDb(user.lockedUntil()))
                    .param("lastLoginAt", toDb(user.lastLoginAt()))
                    .param("createdAt", toDb(user.createdAt()))
                    .param("updatedAt", toDb(user.updatedAt()))
                    .param("version", user.version())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DomainException(IdentityErrors.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }
    }

    @Override
    public void update(User user) {
        int updated = jdbc.sql("""
                UPDATE users SET display_name = :displayName, base_currency = :baseCurrency, timezone = :timezone,
                       status = :status, email_verified_at = :emailVerifiedAt, failed_login_attempts = :failedLoginAttempts, locked_until = :lockedUntil,
                       last_login_at = :lastLoginAt, updated_at = :updatedAt, version = version + 1
                WHERE id = :id AND version = :version
                """)
                .param("id", user.id())
                .param("displayName", user.displayName())
                .param("baseCurrency", user.baseCurrency().getCurrencyCode())
                .param("timezone", user.timezone().getId())
                .param("status", user.status().name())
                .param("emailVerifiedAt", toDb(user.emailVerifiedAt()))
                .param("failedLoginAttempts", user.failedLoginAttempts())
                .param("lockedUntil", toDb(user.lockedUntil()))
                .param("lastLoginAt", toDb(user.lastLoginAt()))
                .param("updatedAt", toDb(user.updatedAt()))
                .param("version", user.version())
                .update();
        if (updated == 0) {
            throw new OptimisticLockingFailureException("User " + user.id() + " was modified concurrently");
        }
        user.incrementVersion();
    }

    @Override
    public void deleteWithAllData(User user) {
        String id = user.id().toString();
        jdbc.sql("DELETE FROM email_verification_token WHERE user_id = :id").param("id", user.id()).update();
        jdbc.sql("DELETE FROM email_outbox WHERE recipient = :email").param("email", user.email().value()).update();
        // The authorization server keys authorizations and consents on the principal name, which is the user id.
        jdbc.sql("DELETE FROM oauth2_authorization WHERE principal_name = :id").param("id", id).update();
        jdbc.sql("DELETE FROM oauth2_authorization_consent WHERE principal_name = :id").param("id", id).update();
        jdbc.sql("DELETE FROM auth_audit_event WHERE user_id = :uid OR principal = :email")
                .param("uid", user.id()).param("email", user.email().value()).update();
        jdbc.sql("DELETE FROM users WHERE id = :uid").param("uid", user.id()).update();
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        return User.rehydrate(
                rs.getObject("id", UUID.class),
                new Email(rs.getString("email")),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                Currency.getInstance(rs.getString("base_currency")),
                ZoneId.of(rs.getString("timezone")),
                UserStatus.valueOf(rs.getString("status")),
                fromDb(rs.getTimestamp("email_verified_at")),
                rs.getInt("failed_login_attempts"),
                fromDb(rs.getTimestamp("locked_until")),
                fromDb(rs.getTimestamp("last_login_at")),
                fromDb(rs.getTimestamp("created_at")),
                fromDb(rs.getTimestamp("updated_at")),
                rs.getLong("version"));
    }

    public static OffsetDateTime toDb(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant fromDb(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
