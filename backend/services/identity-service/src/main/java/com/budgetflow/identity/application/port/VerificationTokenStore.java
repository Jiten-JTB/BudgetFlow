package com.budgetflow.identity.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Email verification tokens, stored only as SHA-256 hashes. */
public interface VerificationTokenStore {

    void save(String tokenHash, UUID userId, Instant expiresAt, Instant issuedAt);

    Optional<UUID> findUserByValidHash(String tokenHash, Instant now);

    Optional<Instant> latestIssuedAt(UUID userId);

    void deleteForUser(UUID userId);
}
