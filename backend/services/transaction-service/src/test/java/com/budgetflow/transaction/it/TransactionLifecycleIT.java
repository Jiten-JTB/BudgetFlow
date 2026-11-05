package com.budgetflow.transaction.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.transaction.application.RecordTransactionCommand;
import com.budgetflow.transaction.application.TransactionService;
import com.budgetflow.transaction.domain.Transaction;
import com.budgetflow.transaction.domain.TransactionErrors;
import com.budgetflow.transaction.domain.TransactionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Saga-facing state transitions against the real database. */
class TransactionLifecycleIT extends TransactionIntegrationTest {

    @Autowired TransactionService transactions;
    @Autowired JdbcClient jdbc;

    private final UUID user = UUID.randomUUID();

    @Test
    void completeIsIdempotentAndPublishesOnce() {
        Transaction t = record("key-life-00001");
        UUID saga = UUID.randomUUID();

        transactions.complete(user, t.id(), saga);
        Transaction again = transactions.complete(user, t.id(), saga);

        assertThat(again.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(events(t.id(), "transaction.TransactionCompleted")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT saga_id FROM money_transaction WHERE id = :id").param("id", t.id())
                .query(UUID.class).single()).isEqualTo(saga);
    }

    @Test
    void cancelRecordsReasonAndBlocksCompletion() {
        Transaction t = record("key-life-00002");

        transactions.cancel(user, t.id(), "ACCOUNT_ARCHIVED", "Account is archived");
        transactions.cancel(user, t.id(), "ACCOUNT_ARCHIVED", "Account is archived");

        assertThat(events(t.id(), "transaction.TransactionCancelled")).isEqualTo(1);
        assertThatThrownBy(() -> transactions.complete(user, t.id(), UUID.randomUUID()))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.INVALID_STATE_TRANSITION);
        var row = jdbc.sql("SELECT status, failure_code FROM money_transaction WHERE id = :id").param("id", t.id())
                .query().singleRow();
        assertThat(row).containsEntry("status", "CANCELLED").containsEntry("failure_code", "ACCOUNT_ARCHIVED");
    }

    @Test
    void otherUsersCannotDriveTheLifecycle() {
        Transaction t = record("key-life-00003");

        assertThatThrownBy(() -> transactions.complete(UUID.randomUUID(), t.id(), UUID.randomUUID()))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(TransactionErrors.TRANSACTION_NOT_FOUND);
    }

    private Transaction record(String key) {
        return transactions.record(user, new RecordTransactionCommand("EXPENSE", UUID.randomUUID(), null, "42", "INR",
                null, null, "2026-11-03", null, null), key).transaction();
    }

    private long events(UUID id, String type) {
        return jdbc.sql("SELECT count(*) FROM outbox_message WHERE aggregate_id = :id AND message_type = :type")
                .param("id", id.toString()).param("type", type).query(Long.class).single();
    }
}
