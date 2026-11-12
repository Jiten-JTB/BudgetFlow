package com.budgetflow.identity.adapter.out.mail;

import static com.budgetflow.identity.adapter.out.persistence.JdbcUserRepository.toDb;

import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.identity.application.port.EmailOutbox;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcEmailOutbox implements EmailOutbox {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcEmailOutbox(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void enqueue(Message message) {
        var now = toDb(clock.instant());
        jdbc.sql("""
                INSERT INTO email_outbox (id, recipient, subject, body_text, body_html, next_attempt_at, created_at)
                VALUES (:id, :to, :subject, :text, :html, :now, :now)""")
                .param("id", UuidV7.generate()).param("to", message.to()).param("subject", message.subject())
                .param("text", message.text()).param("html", message.html()).param("now", now)
                .update();
    }
}
