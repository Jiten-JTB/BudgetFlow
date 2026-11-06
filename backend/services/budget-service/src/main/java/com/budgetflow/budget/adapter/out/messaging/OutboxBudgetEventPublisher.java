package com.budgetflow.budget.adapter.out.messaging;

import com.budgetflow.budget.application.port.BudgetEventPublisher;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetStatus;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import java.util.UUID;

/** Integration event contracts v1 (additive changes only), consumed by notification-service. */
public class OutboxBudgetEventPublisher implements BudgetEventPublisher {

    private final OutboxWriter outbox;

    public OutboxBudgetEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void thresholdReached(BudgetStatus status) {
        publish(status, "budget.threshold-reached", "budget.BudgetThresholdReached");
    }

    @Override
    public void exceeded(BudgetStatus status) {
        publish(status, "budget.exceeded", "budget.BudgetExceeded");
    }

    private void publish(BudgetStatus s, String routingKey, String type) {
        Budget b = s.budget();
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS).routingKey(routingKey).type(type)
                .aggregate("Budget", b.id().toString())
                .payload(new BudgetAlert(b.id(), b.userId(), b.name(), b.categoryId(), b.period().name(),
                        s.period().start().toString(), s.period().end().toString(), b.limit().amount().toPlainString(),
                        s.spent().amount().toPlainString(), b.currency().getCurrencyCode(), s.percentUsed(),
                        b.alertThresholdPercent()))
                .build());
    }

    record BudgetAlert(UUID budgetId, UUID userId, String name, UUID categoryId, String period, String periodStart,
                       String periodEnd, String limit, String spent, String currency, int percentUsed,
                       int alertThresholdPercent) {}
}
