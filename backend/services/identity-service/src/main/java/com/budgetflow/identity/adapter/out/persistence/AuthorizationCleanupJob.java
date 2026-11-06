package com.budgetflow.identity.adapter.out.persistence;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/** Spring Authorization Server never deletes rows; purge authorizations whose every token has expired. */
public class AuthorizationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(1);

    private final JdbcClient jdbc;

    public AuthorizationCleanupJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${budgetflow.identity.authorization-cleanup-cron:0 17 3 * * *}")
    void scheduledPurge() {
        int deleted = purgeExpired(RETENTION);
        log.info("Purged {} expired OAuth2 authorizations", deleted);
    }

    /** GREATEST ignores NULLs, so a row stays while any of its tokens is still valid within retention. */
    public int purgeExpired(Duration retention) {
        return jdbc.sql("""
                DELETE FROM oauth2_authorization
                WHERE GREATEST(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at,
                               oidc_id_token_expires_at, user_code_expires_at, device_code_expires_at)
                      < now() - make_interval(secs => :retentionSeconds)
                """)
                .param("retentionSeconds", retention.toSeconds())
                .update();
    }
}
