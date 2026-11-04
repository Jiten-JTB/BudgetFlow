package com.budgetflow.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.identity.adapter.out.persistence.AuthorizationCleanupJob;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuthorizationCleanupIT extends IdentityIntegrationTest {

    @Autowired AuthorizationCleanupJob job;
    @Autowired JdbcClient jdbc;

    @Test
    void deletesOnlyAuthorizationsWhoseTokensExpiredBeforeRetention() {
        insert("expired", "now() - interval '3 days'", "now() - interval '2 days'");
        insert("refresh-still-valid", "now() - interval '3 days'", "now() + interval '10 days'");
        insert("recently-expired", "now() - interval '1 hour'", "now() - interval '10 minutes'");

        int deleted = job.purgeExpired(Duration.ofDays(1));

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.sql("SELECT id FROM oauth2_authorization WHERE id IN ('expired','refresh-still-valid','recently-expired') ORDER BY id")
                .query(String.class).list()).containsExactly("recently-expired", "refresh-still-valid");
    }

    private void insert(String id, String accessExpiry, String refreshExpiry) {
        jdbc.sql("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type,
                     access_token_expires_at, refresh_token_expires_at)
                VALUES (:id, 'c', 'p', 'authorization_code', %s, %s)
                """.formatted(accessExpiry, refreshExpiry))
                .param("id", id).update();
    }
}
