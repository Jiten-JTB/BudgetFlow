package com.budgetflow.orchestrator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SagaInstanceTest {

    private static final Instant T0 = Instant.parse("2026-11-06T10:00:00Z");
    private static final SagaPolicy POLICY = new SagaPolicy(Duration.ofSeconds(30), 3);

    private SagaInstance saga;
    private PendingCommand pending;

    @BeforeEach
    void start() {
        saga = SagaInstance.create(UUID.randomUUID(), SagaType.RECORD_TRANSACTION, SagaDefinitionsTest.data("EXPENSE"), T0);
        pending = saga.start(T0, POLICY);
    }

    @Test
    void startsWithFirstStepAndDeadline() {
        assertThat(pending.type()).isEqualTo("account.ApplyBalanceChange");
        assertThat(saga.status()).isEqualTo(SagaStatus.RUNNING);
        assertThat(saga.awaitingCommandId()).isEqualTo(pending.commandId());
        assertThat(saga.deadlineAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(saga.drainEvents()).extracting(SagaEvent::type)
                .containsExactly(SagaEventType.STARTED, SagaEventType.COMMAND_SENT);
    }

    @Test
    void happyPathRunsEveryStepThenCompletes() {
        assertThat(succeed().type()).isEqualTo("budget.ConsumeBudget");
        assertThat(succeed().type()).isEqualTo("transaction.CompleteTransaction");
        assertThat(saga.onReply(pending.commandId(), true, null, null, T0, POLICY)).isEmpty();

        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.phase()).isEqualTo(SagaPhase.DONE);
        assertThat(saga.awaitingCommandId()).isNull();
        assertThat(saga.deadlineAt()).isNull();
        assertThat(saga.finishedAt()).isEqualTo(T0);
    }

    @Test
    void businessFailureCompensatesCompletedStepsInReverseThenCancels() {
        succeed(); // balance applied
        assertThat(fail("BUDGET_BROKEN", "boom").type()).as("failed step itself is released (safe no-op)")
                .isEqualTo("budget.ReleaseBudget");
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATING);
        assertThat(saga.failureCode()).isEqualTo("BUDGET_BROKEN");
        assertThat(succeed().type()).isEqualTo("account.ReverseBalanceChange");
        PendingCommand cancel = succeed();
        assertThat(cancel.type()).isEqualTo("transaction.CancelTransaction");
        assertThat(cancel.fields()).containsEntry("failureCode", "BUDGET_BROKEN").containsEntry("failureReason", "boom");

        assertThat(saga.onReply(cancel.commandId(), true, null, null, T0, POLICY)).isEmpty();
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
    }

    @Test
    void failureOfFirstStepStillLeavesTombstoneThenCancels() {
        assertThat(fail("ACCOUNT_ARCHIVED", "Account is archived").type()).isEqualTo("account.ReverseBalanceChange");
        assertThat(succeed().type()).isEqualTo("transaction.CancelTransaction");
    }

    @Test
    void ignoresStaleAndDuplicateReplies() {
        UUID first = pending.commandId();
        succeed();
        saga.drainEvents();

        assertThat(saga.onReply(first, true, null, null, T0, POLICY)).isEmpty();
        assertThat(saga.onReply(UUID.randomUUID(), false, "X", "y", T0, POLICY)).isEmpty();

        assertThat(saga.awaitingCommandType()).isEqualTo("budget.ConsumeBudget");
        assertThat(saga.drainEvents()).extracting(SagaEvent::type)
                .containsExactly(SagaEventType.STALE_REPLY_IGNORED, SagaEventType.STALE_REPLY_IGNORED);
    }

    @Test
    void timedOutForwardStepIsCompensatedIncludingItself() {
        assertThat(saga.onTimeout(T0.plusSeconds(29), POLICY)).as("not due yet").isEmpty();

        Optional<PendingCommand> next = saga.onTimeout(T0.plusSeconds(31), POLICY);

        assertThat(next).map(PendingCommand::type).contains("account.ReverseBalanceChange");
        assertThat(saga.failureCode()).isEqualTo("STEP_TIMEOUT");
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATING);
    }

    @Test
    void retriableStepIsResentOnTimeoutThenCompensatedWhenAttemptsRunOut() {
        succeed();
        PendingCommand complete = succeed();
        Instant t = T0;
        for (int attempt = 2; attempt <= 3; attempt++) {
            t = t.plusSeconds(31);
            PendingCommand resent = saga.onTimeout(t, POLICY).orElseThrow();
            assertThat(resent.type()).isEqualTo("transaction.CompleteTransaction");
            assertThat(resent.commandId()).isNotEqualTo(complete.commandId());
            assertThat(saga.attempts()).isEqualTo(attempt);
            complete = resent;
        }

        PendingCommand next = saga.onTimeout(t.plusSeconds(31), POLICY).orElseThrow();

        assertThat(next.type()).isEqualTo("budget.ReleaseBudget");
        assertThat(saga.failureCode()).isEqualTo("STEP_TIMEOUT");
    }

    @Test
    void failedCompensationNeedsManualIntervention() {
        fail("X", "y");

        assertThat(saga.onReply(pending.commandId(), false, "DB_DOWN", "nope", T0, POLICY)).isEmpty();

        assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.failureCode()).isEqualTo("COMPENSATION_FAILED");
        assertThat(saga.failureReason()).contains("account.ReverseBalanceChange", "DB_DOWN");
        assertThat(saga.deadlineAt()).isNull();
    }

    @Test
    void compensationIsRetriedOnTimeoutThenFails() {
        fail("X", "y");
        Instant t = T0;
        for (int i = 0; i < 2; i++) {
            t = t.plusSeconds(31);
            assertThat(saga.onTimeout(t, POLICY)).map(PendingCommand::type).contains("account.ReverseBalanceChange");
        }

        assertThat(saga.onTimeout(t.plusSeconds(31), POLICY)).isEmpty();
        assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    void terminalSagasIgnoreEverything() {
        fail("X", "y");
        saga.onReply(pending.commandId(), false, "Z", "z", T0, POLICY); // FAILED

        assertThat(saga.onTimeout(T0.plusSeconds(3600), POLICY)).isEmpty();
        assertThat(saga.onReply(UUID.randomUUID(), true, null, null, T0, POLICY)).isEmpty();
    }

    private PendingCommand succeed() {
        pending = saga.onReply(pending.commandId(), true, null, null, T0, POLICY).orElseThrow();
        return pending;
    }

    private PendingCommand fail(String code, String message) {
        pending = saga.onReply(pending.commandId(), false, code, message, T0, POLICY).orElseThrow();
        return pending;
    }
}
