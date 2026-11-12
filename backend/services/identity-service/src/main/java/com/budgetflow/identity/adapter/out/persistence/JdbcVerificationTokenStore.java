package com.budgetflow.identity.adapter.out.persistence;

import static com.budgetflow.identity.adapter.out.persistence.JdbcUserRepository.toDb;

import com.budgetflow.identity.application.port.VerificationTokenStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcVerificationTokenStore implements VerificationTokenStore {

    private final JdbcClient jdbc;

    public JdbcVerificationTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String tokenHash, UUID userId, Instant expiresAt, Instant issuedAt) {
        jdbc.sql("""
                INSERT INTO email_verification_token (token_hash, user_id, expires_at, created_at)
                VALUES (:hash, :userId, :expiresAt, :createdAt)""")
                .param("hash", tokenHash).param("userId", userId)
                .param("expiresAt", toDb(expiresAt)).param("createdAt", toDb(issuedAt))
                .update();
    }

    @Override
    public Optional<UUID> findUserByValidHash(String tokenHash, Instant now) {
        return jdbc.sql("SELECT user_id FROM email_verification_token WHERE token_hash = :hash AND expires_at > :now")
                .param("hash", tokenHash).param("now", toDb(now))
                .query(UUID.class).optional();
    }

    @Override
    public Optional<Instant> latestIssuedAt(UUID userId) {
        return jdbc.sql("SELECT max(created_at) FROM email_verification_token WHERE user_id = :userId")
                .param("userId", userId)
                .query((rs, row) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single();
    }

    @Override
    public void deleteForUser(UUID userId) {
        jdbc.sql("DELETE FROM email_verification_token WHERE user_id = :userId").param("userId", userId).update();
    }
}
