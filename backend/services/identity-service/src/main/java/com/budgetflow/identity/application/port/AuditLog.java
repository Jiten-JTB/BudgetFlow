package com.budgetflow.identity.application.port;

import com.budgetflow.identity.application.AuditEvent;

public interface AuditLog {

    void record(AuditEvent event);
}
