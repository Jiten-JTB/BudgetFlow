package com.budgetflow.budget.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.budget.application.BudgetService;
import com.budgetflow.budget.application.CreateBudgetCommand;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.common.test.SagaCommands;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** budget-service as a saga participant, over real RabbitMQ. */
class SagaCommandsIT extends BudgetIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;
    @Autowired BudgetService budgets;

    @Test
    void consumesViaParentCategoryAndReleases() {
        UUID user = UUID.randomUUID();
        UUID food = UUID.randomUUID();
        Budget budget = budgets.create(user, new CreateBudgetCommand("Food", food, "MONTHLY", "1000", "INR", 80, "2026-11-01"));
        String key = "txn:" + UUID.randomUUID() + ":budget";
        Map<String, Object> consume = new HashMap<>(Map.of("userId", user, "transactionId", UUID.randomUUID(),
                "categoryId", UUID.randomUUID(), "parentCategoryId", food, "amount", "420", "currency", "INR",
                "occurredOn", "2026-11-05", "operationKey", key));

        assertThat(SagaCommands.sendAndAwaitReply(rabbit, jdbc, "budget.ConsumeBudget", "budget.consume-budget", consume))
                .contains("\"outcome\":\"SUCCESS\"");
        assertThat(budgets.status(user, budget.id(), "2026-11-05").spent().amount()).isEqualByComparingTo("420");

        assertThat(SagaCommands.sendAndAwaitReply(rabbit, jdbc, "budget.ReleaseBudget", "budget.release-budget",
                Map.of("userId", user, "operationKey", key))).contains("\"outcome\":\"SUCCESS\"");
        assertThat(budgets.status(user, budget.id(), "2026-11-05").spent().amount()).isEqualByComparingTo("0");
    }

    @Test
    void uncategorisedExpenseWithoutBudgetsSucceeds() {
        Map<String, Object> consume = new HashMap<>();
        consume.put("userId", UUID.randomUUID());
        consume.put("transactionId", UUID.randomUUID());
        consume.put("categoryId", null);
        consume.put("parentCategoryId", null);
        consume.put("amount", "10");
        consume.put("currency", "INR");
        consume.put("occurredOn", "2026-11-05");
        consume.put("operationKey", "txn:" + UUID.randomUUID() + ":budget");

        assertThat(SagaCommands.sendAndAwaitReply(rabbit, jdbc, "budget.ConsumeBudget", "budget.consume-budget", consume))
                .contains("\"outcome\":\"SUCCESS\"");
    }
}
