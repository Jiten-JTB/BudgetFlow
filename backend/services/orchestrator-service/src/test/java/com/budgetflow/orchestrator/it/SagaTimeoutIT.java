package com.budgetflow.orchestrator.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.orchestrator.it.SagaHarness.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "budgetflow.orchestrator.step-timeout=1s")
class SagaTimeoutIT extends OrchestratorIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired JdbcClient jdbc;

    @Test
    void unansweredStepTimesOutAndIsCompensated() {
        SagaHarness harness = new SagaHarness(rabbit, admin, "timeout");
        UUID txn = UUID.randomUUID();
        harness.publishTransactionRequested(txn, UUID.randomUUID(), "EXPENSE", UUID.randomUUID(), null);

        Command apply = harness.nextCommand();               // never answered
        Command reverse = harness.nextCommand();

        assertThat(reverse.type()).isEqualTo("account.ReverseBalanceChange");
        assertThat(reverse.body().get("operationKey").asString()).isEqualTo(apply.body().get("operationKey").asString());
        assertThat(jdbc.sql("SELECT failure_code FROM saga_instance WHERE business_key = :k")
                .param("k", txn.toString()).query(String.class).single()).isEqualTo("STEP_TIMEOUT");
    }
}
