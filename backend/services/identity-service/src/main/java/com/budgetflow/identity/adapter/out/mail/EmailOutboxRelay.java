package com.budgetflow.identity.adapter.out.mail;

import static com.budgetflow.identity.adapter.out.persistence.JdbcUserRepository.toDb;

import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers queued emails over SMTP. Rows are claimed with SKIP LOCKED so several instances never send one twice;
 * a sent email is deleted (its link is a secret). Failures retry with backoff, then stop and log for a human.
 */
public class EmailOutboxRelay {

    static final int MAX_ATTEMPTS = 8;
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxRelay.class);

    private final JdbcClient jdbc;
    private final JavaMailSender mail;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String from;

    public EmailOutboxRelay(JdbcClient jdbc, JavaMailSender mail, TransactionTemplate tx, Clock clock, String from) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.tx = tx;
        this.clock = clock;
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${budgetflow.identity.mail.relay-interval:2s}")
    public void deliverDue() {
        List<UUID> due = jdbc.sql("""
                SELECT id FROM email_outbox WHERE attempts < :max AND next_attempt_at <= :now
                ORDER BY next_attempt_at LIMIT 20""")
                .param("max", MAX_ATTEMPTS).param("now", toDb(clock.instant()))
                .query(UUID.class).list();
        due.forEach(id -> tx.executeWithoutResult(status -> deliver(id)));
    }

    private void deliver(UUID id) {
        var row = jdbc.sql("""
                SELECT recipient, subject, body_text, body_html, attempts FROM email_outbox
                WHERE id = :id AND attempts < :max FOR UPDATE SKIP LOCKED""")
                .param("id", id).param("max", MAX_ATTEMPTS)
                .query((rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
                .optional();
        if (row.isEmpty()) {
            return;
        }
        Row r = row.get();
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, r.html() != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(r.to());
            helper.setSubject(r.subject());
            if (r.html() != null) {
                helper.setText(r.text(), r.html());
            } else {
                helper.setText(r.text());
            }
            mail.send(message);
            jdbc.sql("DELETE FROM email_outbox WHERE id = :id").param("id", id).update();
        } catch (Exception e) {
            int attempts = r.attempts() + 1;
            Instant next = clock.instant().plus(backoff(attempts));
            String error = String.valueOf(e.getMessage());
            jdbc.sql("UPDATE email_outbox SET attempts = :a, next_attempt_at = :next, last_error = :err WHERE id = :id")
                    .param("a", attempts).param("next", toDb(next))
                    .param("err", error.substring(0, Math.min(500, error.length()))).param("id", id).update();
            if (attempts >= MAX_ATTEMPTS) {
                log.error("EMAIL UNDELIVERABLE after {} attempts: id={} error={}", attempts, id, error);
            } else {
                log.warn("Email delivery failed (attempt {}), retrying at {}: id={} error={}", attempts, next, id, error);
            }
        }
    }

    /** 30s, 1m, 2m, 4m … capped at 1h. */
    static Duration backoff(int attempts) {
        long seconds = 30L << Math.min(attempts - 1, 7);
        return Duration.ofSeconds(Math.min(seconds, 3600));
    }

    private record Row(String to, String subject, String text, String html, int attempts) {}
}
