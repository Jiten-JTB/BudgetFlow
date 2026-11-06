package com.budgetflow.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.budgetflow.orchestrator.it.SagaHarness.Command;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SagaFlowIT extends OrchestratorIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcClient jdbc;

    private SagaHarness harness;
    private final UUID txn = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        harness = new SagaHarness(rabbit, admin, "flow");
    }

    @Test
    void expenseSagaRunsAllStepsAndCompletes() {
        harness.publishTransactionRequested(txn, user, "EXPENSE", account, null);

        Command apply = harness.nextCommand();
        assertThat(apply.type()).isEqualTo("account.ApplyBalanceChange");
        assertThat(apply.body().get("amount").asString()).isEqualTo("-850.0000");
        assertThat(apply.body().get("operationKey").asString()).isEqualTo("txn:" + txn + ":apply");
        harness.succeed(apply);

        Command consume = harness.nextCommand();
        assertThat(consume.type()).isEqualTo("budget.ConsumeBudget");
        assertThat(consume.sagaId()).isEqualTo(apply.sagaId());
        harness.succeed(consume);

        Command complete = harness.nextCommand();
        assertThat(complete.type()).isEqualTo("transaction.CompleteTransaction");
        harness.succeed(complete);

        awaitStatus("COMPLETED");
        assertThat(jdbc.sql("SELECT count(*) FROM saga_event WHERE saga_id = :id").param("id", apply.sagaId())
                .query(Long.class).single()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void failedStepIsCompensatedInReverseOrderThenTransactionCancelled() {
        harness.publishTransactionRequested(txn, user, "EXPENSE", account, null);
        harness.succeed(harness.nextCommand());                 // balance applied
        harness.fail(harness.nextCommand(), "BUDGET_DOWN", "x"); // budget refused

        Command release = harness.nextCommand();
        assertThat(release.type()).isEqualTo("budget.ReleaseBudget");
        harness.succeed(release);
        Command reverse = harness.nextCommand();
        assertThat(reverse.type()).isEqualTo("account.ReverseBalanceChange");
        assertThat(reverse.body().get("operationKey").asString()).isEqualTo("txn:" + txn + ":apply");
        harness.succeed(reverse);
        Command cancel = harness.nextCommand();
        assertThat(cancel.type()).isEqualTo("transaction.CancelTransaction");
        assertThat(cancel.body().get("failureCode").asString()).isEqualTo("BUDGET_DOWN");
        harness.succeed(cancel);

        awaitStatus("COMPENSATED");
    }

    @Test
    void duplicateTriggerStartsOnlyOneSaga() {
        harness.publishTransactionRequested(txn, user, "INCOME", account, null);
        harness.publishTransactionRequested(txn, user, "INCOME", account, null);

        Command apply = harness.nextCommand();
        assertThat(apply.body().get("amount").asString()).isEqualTo("850.0000");
        harness.assertNoCommand(1_500);
        assertThat(jdbc.sql("SELECT count(*) FROM saga_instance WHERE business_key = :k").param("k", txn.toString())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void staleReplyDoesNotAdvanceTheSaga() {
        harness.publishTransactionRequested(txn, user, "TRANSFER", account, UUID.randomUUID());
        Command debit = harness.nextCommand();
        assertThat(debit.type()).isEqualTo("account.ApplyBalanceChange");
        assertThat(debit.body().get("operationKey").asString()).isEqualTo("txn:" + txn + ":debit");

        harness.succeed(new Command(debit.type(), debit.sagaId(), UUID.randomUUID(), debit.body()));
        harness.assertNoCommand(1_500);

        harness.succeed(debit);
        assertThat(harness.nextCommand().body().get("operationKey").asString()).isEqualTo("txn:" + txn + ":credit");
    }

    private void awaitStatus(String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("SELECT status FROM saga_instance WHERE business_key = :k")
                        .param("k", txn.toString()).query(String.class).single()).isEqualTo(status));
    }
}
