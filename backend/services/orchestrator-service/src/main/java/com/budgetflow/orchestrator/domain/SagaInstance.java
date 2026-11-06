package com.budgetflow.orchestrator.domain;

import com.budgetflow.common.core.id.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted saga state machine. One command is outstanding at a time.
 * <pre>
 * FORWARD    success -> next step | COMPLETED          failure/timeout -> COMPENSATE from the current step down
 * COMPENSATE success -> next lower compensation | CANCEL   failure/attempts exhausted -> FAILED
 * CANCEL     success -> COMPENSATED                     failure/attempts exhausted -> FAILED
 * </pre>
 * The failing or timed-out step is compensated too: participants make that a no-op that also blocks a late
 * original (tombstone), which is what makes timeouts safe.
 */
public final class SagaInstance {

    private static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final SagaType type;
    private final TransactionSagaData data;
    private final List<SagaStep> steps;
    private SagaStatus status;
    private SagaPhase phase;
    private int stepIndex;
    private PendingCommand awaiting;
    private int attempts;
    private Instant deadlineAt;
    private String failureCode;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;
    private long version;
    private final List<SagaEvent> events = new ArrayList<>();

    private SagaInstance(UUID id, SagaType type, TransactionSagaData data, SagaStatus status, SagaPhase phase,
                         int stepIndex, PendingCommand awaiting, int attempts, Instant deadlineAt, String failureCode,
                         String failureReason, Instant createdAt, Instant updatedAt, Instant finishedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.data = Objects.requireNonNull(data);
        this.steps = SagaDefinitions.steps(type, data);
        this.status = status;
        this.phase = phase;
        this.stepIndex = stepIndex;
        this.awaiting = awaiting;
        this.attempts = attempts;
        this.deadlineAt = deadlineAt;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.finishedAt = finishedAt;
        this.version = version;
    }

    public static SagaInstance create(UUID id, SagaType type, TransactionSagaData data, Instant now) {
        return new SagaInstance(id, type, data, SagaStatus.RUNNING, SagaPhase.FORWARD, 0, null, 0, null, null, null,
                now, now, null, 0);
    }

    /** @param awaitingCommandId the outstanding command; it is re-derived from phase and step on load */
    public static SagaInstance rehydrate(UUID id, SagaType type, TransactionSagaData data, SagaStatus status,
                                         SagaPhase phase, int stepIndex, UUID awaitingCommandId, int attempts,
                                         Instant deadlineAt, String failureCode, String failureReason,
                                         Instant createdAt, Instant updatedAt, Instant finishedAt, long version) {
        SagaInstance saga = new SagaInstance(id, type, data, status, phase, stepIndex, null, attempts, deadlineAt,
                failureCode, failureReason, createdAt, updatedAt, finishedAt, version);
        if (awaitingCommandId != null) {
            SagaCommand command = saga.commandFor(phase, stepIndex);
            saga.awaiting = new PendingCommand(awaitingCommandId, command.type(), command.fields());
        }
        return saga;
    }

    public PendingCommand start(Instant now, SagaPolicy policy) {
        record(SagaEventType.STARTED, null, null, type.name(), now);
        return issue(steps.getFirst().forward(), now, policy);
    }

    public Optional<PendingCommand> onReply(UUID commandId, boolean success, String errorCode, String errorMessage,
                                            Instant now, SagaPolicy policy) {
        if (!status.isActive() || awaiting == null || !awaiting.commandId().equals(commandId)) {
            record(SagaEventType.STALE_REPLY_IGNORED, null, commandId, status.name(), now);
            return Optional.empty();
        }
        String commandType = awaiting.type();
        record(success ? SagaEventType.REPLY_SUCCESS : SagaEventType.REPLY_FAILURE, commandType, commandId,
                success ? null : errorCode + ": " + errorMessage, now);
        return switch (phase) {
            case FORWARD -> success ? advance(now, policy) : fail(errorCode, errorMessage, stepIndex, now, policy);
            case COMPENSATE -> success
                    ? compensateFrom(stepIndex - 1, now, policy)
                    : giveUp("COMPENSATION_FAILED", commandType + " failed: " + errorCode + " " + errorMessage, now);
            case CANCEL -> success
                    ? finish(SagaStatus.COMPENSATED, SagaEventType.COMPENSATED, now)
                    : giveUp("COMPENSATION_FAILED", commandType + " failed: " + errorCode + " " + errorMessage, now);
            case DONE -> Optional.empty();
        };
    }

    public Optional<PendingCommand> onTimeout(Instant now, SagaPolicy policy) {
        if (!status.isActive() || deadlineAt == null || now.isBefore(deadlineAt)) {
            return Optional.empty();
        }
        record(SagaEventType.TIMED_OUT, awaiting.type(), awaiting.commandId(), "attempt " + attempts, now);
        boolean resendable = phase != SagaPhase.FORWARD || steps.get(stepIndex).retriable();
        if (resendable && attempts < policy.maxAttempts()) {
            return Optional.of(resend(now, policy));
        }
        if (phase == SagaPhase.FORWARD) {
            return fail("STEP_TIMEOUT", "No reply to " + awaiting.type() + " after " + attempts + " attempt(s)",
                    stepIndex, now, policy);
        }
        return giveUp("COMPENSATION_TIMEOUT", "No reply to " + awaiting.type() + " after " + attempts + " attempts", now);
    }

    public List<SagaEvent> drainEvents() {
        List<SagaEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    private Optional<PendingCommand> advance(Instant now, SagaPolicy policy) {
        stepIndex++;
        if (stepIndex == steps.size()) {
            return finish(SagaStatus.COMPLETED, SagaEventType.COMPLETED, now);
        }
        return Optional.of(issue(steps.get(stepIndex).forward(), now, policy));
    }

    private Optional<PendingCommand> fail(String code, String reason, int fromStep, Instant now, SagaPolicy policy) {
        failureCode = code;
        failureReason = truncate(reason);
        status = SagaStatus.COMPENSATING;
        return compensateFrom(fromStep, now, policy);
    }

    /** Compensates the highest step at or below {@code from} that has a compensation, else cancels. */
    private Optional<PendingCommand> compensateFrom(int from, Instant now, SagaPolicy policy) {
        for (int i = from; i >= 0; i--) {
            if (steps.get(i).compensation() != null) {
                phase = SagaPhase.COMPENSATE;
                stepIndex = i;
                return Optional.of(issue(steps.get(i).compensation(), now, policy));
            }
        }
        phase = SagaPhase.CANCEL;
        return Optional.of(issue(SagaDefinitions.cancel(data, failureCode, failureReason), now, policy));
    }

    private Optional<PendingCommand> finish(SagaStatus terminal, SagaEventType event, Instant now) {
        status = terminal;
        phase = SagaPhase.DONE;
        clearAwaiting(now);
        finishedAt = now;
        record(event, null, null, failureCode, now);
        return Optional.empty();
    }

    private Optional<PendingCommand> giveUp(String code, String reason, Instant now) {
        failureCode = code;
        failureReason = truncate(reason);
        return finish(SagaStatus.FAILED, SagaEventType.FAILED, now);
    }

    private PendingCommand issue(SagaCommand command, Instant now, SagaPolicy policy) {
        awaiting = new PendingCommand(UuidV7.generate(), command.type(), command.fields());
        attempts = 1;
        deadlineAt = now.plus(policy.stepTimeout());
        updatedAt = now;
        record(SagaEventType.COMMAND_SENT, awaiting.type(), awaiting.commandId(), null, now);
        return awaiting;
    }

    private PendingCommand resend(Instant now, SagaPolicy policy) {
        awaiting = new PendingCommand(UuidV7.generate(), awaiting.type(), awaiting.fields());
        attempts++;
        deadlineAt = now.plus(policy.stepTimeout());
        updatedAt = now;
        record(SagaEventType.COMMAND_RESENT, awaiting.type(), awaiting.commandId(), "attempt " + attempts, now);
        return awaiting;
    }

    private void clearAwaiting(Instant now) {
        awaiting = null;
        deadlineAt = null;
        updatedAt = now;
    }

    private SagaCommand commandFor(SagaPhase p, int index) {
        return switch (p) {
            case FORWARD -> steps.get(index).forward();
            case COMPENSATE -> steps.get(index).compensation();
            case CANCEL -> SagaDefinitions.cancel(data, failureCode, failureReason);
            case DONE -> throw new IllegalStateException("A finished saga awaits nothing");
        };
    }

    private void record(SagaEventType eventType, String commandType, UUID commandId, String detail, Instant now) {
        events.add(new SagaEvent(eventType, commandType, commandId, truncate(detail), now));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_REASON_LENGTH ? value : value.substring(0, MAX_REASON_LENGTH);
    }

    public void incrementVersion() {
        version++;
    }

    public UUID id() { return id; }
    public SagaType type() { return type; }
    public TransactionSagaData data() { return data; }
    public SagaStatus status() { return status; }
    public SagaPhase phase() { return phase; }
    public int stepIndex() { return stepIndex; }
    public UUID awaitingCommandId() { return awaiting == null ? null : awaiting.commandId(); }
    public String awaitingCommandType() { return awaiting == null ? null : awaiting.type(); }
    public int attempts() { return attempts; }
    public Instant deadlineAt() { return deadlineAt; }
    public String failureCode() { return failureCode; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant finishedAt() { return finishedAt; }
    public long version() { return version; }
}
