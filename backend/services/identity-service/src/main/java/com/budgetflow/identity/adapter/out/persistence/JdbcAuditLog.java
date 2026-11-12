package com.budgetflow.identity.adapter.out.persistence;

import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.identity.application.AuditEvent;
import com.budgetflow.identity.application.port.AuditLog;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;

    public JdbcAuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEvent event) {
        jdbc.sql("""
                INSERT INTO auth_audit_event (id, user_id, principal, event_type, remote_address, user_agent, occurred_at)
                VALUES (:id, :userId, :principal, :type, :remoteAddress, :userAgent, :occurredAt)
                """)
                .param("id", UuidV7.generate())
                .param("userId", event.userId())
                .param("principal", event.principal())
                .param("type", event.type().name())
                .param("remoteAddress", event.client() == null ? null : event.client().remoteAddress())
                .param("userAgent", event.client() == null ? null : event.client().userAgent())
                .param("occurredAt", JdbcUserRepository.toDb(event.occurredAt()))
                .update();
    }
}
