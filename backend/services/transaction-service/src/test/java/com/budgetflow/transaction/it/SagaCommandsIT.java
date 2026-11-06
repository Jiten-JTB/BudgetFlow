package com.budgetflow.transaction.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.common.test.SagaCommands;
import com.budgetflow.transaction.application.CategoryService;
import com.budgetflow.transaction.application.CategoryService.CreateCategoryCommand;
import com.budgetflow.transaction.application.RecordTransactionCommand;
import com.budgetflow.transaction.application.TransactionService;
import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.Transaction;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** transaction-service as a saga participant, over real RabbitMQ. */
class SagaCommandsIT extends TransactionIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionService transactions;
    @Autowired CategoryService categories;

    private final UUID user = UUID.randomUUID();

    @Test
    void completesTransaction() {
        Transaction t = record(null);

        String reply = SagaCommands.sendAndAwaitReply(rabbit, jdbc, "transaction.CompleteTransaction",
                "transaction.complete-transaction", Map.of("userId", user, "transactionId", t.id()));

        assertThat(reply).contains("\"outcome\":\"SUCCESS\"");
        assertThat(status(t)).isEqualTo("COMPLETED");
    }

    @Test
    void cancelsWithFailureCodeAndRefusesToCancelCompleted() {
        Transaction pending = record(null);
        assertThat(SagaCommands.sendAndAwaitReply(rabbit, jdbc, "transaction.CancelTransaction",
                "transaction.cancel-transaction", Map.of("userId", user, "transactionId", pending.id(),
                        "failureCode", "ACCOUNT_ARCHIVED", "failureReason", "Account is archived")))
                .contains("\"outcome\":\"SUCCESS\"");
        assertThat(status(pending)).isEqualTo("CANCELLED");

        Transaction done = record(null);
        transactions.complete(user, done.id(), UUID.randomUUID());
        assertThat(SagaCommands.sendAndAwaitReply(rabbit, jdbc, "transaction.CancelTransaction",
                "transaction.cancel-transaction", Map.of("userId", user, "transactionId", done.id(),
                        "failureCode", "X", "failureReason", "y")))
                .contains("\"outcome\":\"FAILURE\"", "\"errorCode\":\"INVALID_STATE_TRANSITION\"");
    }

    @Test
    void transactionRequestedCarriesParentCategoryForBudgetMatching() {
        Category food = categories.create(user, new CreateCategoryCommand("Food", "EXPENSE", null, null, null));
        Category dining = categories.create(user, new CreateCategoryCommand("Dining", "EXPENSE", food.id(), null, null));

        Transaction t = record(dining.id());

        String payload = jdbc.sql("""
                SELECT payload::text FROM outbox_message
                WHERE aggregate_id = :id AND message_type = 'transaction.TransactionRequested'""")
                .param("id", t.id().toString()).query(String.class).single();
        assertThat(payload).contains("\"categoryId\":\"" + dining.id() + "\"", "\"parentCategoryId\":\"" + food.id() + "\"");
    }

    private Transaction record(UUID categoryId) {
        return transactions.record(user, new RecordTransactionCommand("EXPENSE", UUID.randomUUID(), null, "42", "INR",
                categoryId, null, "2026-11-05", null, null), "key-" + UUID.randomUUID()).transaction();
    }

    private String status(Transaction t) {
        return jdbc.sql("SELECT status FROM money_transaction WHERE id = :id").param("id", t.id())
                .query(String.class).single();
    }
}
