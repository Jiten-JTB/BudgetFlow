package com.budgetflow.transaction.adapter.out.messaging;

import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.transaction.application.port.TransactionEventPublisher;
import com.budgetflow.transaction.domain.Transaction;
import java.time.Instant;
import java.util.UUID;

/** Integration event contracts v1 (additive changes only). Amounts are positive decimal strings. */
public class OutboxTransactionEventPublisher implements TransactionEventPublisher {

    private final OutboxWriter outbox;

    public OutboxTransactionEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void requested(Transaction t, UUID parentCategoryId) {
        publish(t, parentCategoryId, "transaction.transaction-requested", "transaction.TransactionRequested", t.updatedAt());
    }

    @Override
    public void completed(Transaction t) {
        publish(t, null, "transaction.transaction-completed", "transaction.TransactionCompleted", t.completedAt());
    }

    @Override
    public void cancelled(Transaction t) {
        publish(t, null, "transaction.transaction-cancelled", "transaction.TransactionCancelled", t.updatedAt());
    }

    private void publish(Transaction t, UUID parentCategoryId, String routingKey, String type, Instant occurredAt) {
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS).routingKey(routingKey).type(type)
                .aggregate("Transaction", t.id().toString())
                .correlationId(t.sagaId())
                .payload(new TransactionEvent(t.id(), t.userId(), t.kind().name(), t.status().name(), t.accountId(),
                        t.counterAccountId(), t.amount().amount().toPlainString(),
                        t.amount().currency().getCurrencyCode(), t.categoryId(), parentCategoryId, t.incomeSourceId(),
                        t.occurredOn().toString(), t.failureCode(), occurredAt))
                .build());
    }

    record TransactionEvent(UUID transactionId, UUID userId, String kind, String status, UUID accountId,
                            UUID counterAccountId, String amount, String currency, UUID categoryId,
                            UUID parentCategoryId, UUID incomeSourceId, String occurredOn, String failureCode, Instant occurredAt) {}
}
