package com.budgetflow.identity.application;

import com.budgetflow.identity.domain.AuditEventType;
import java.time.Instant;
import java.util.UUID;

/** @param userId null when the principal could not be resolved to a user */
public record AuditEvent(UUID userId, String principal, AuditEventType type, ClientInfo client, Instant occurredAt) {}
