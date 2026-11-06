package com.budgetflow.account.adapter.out.messaging;

import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.outbox.OutboxMessage;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import java.time.Instant;
import java.util.UUID;

/** Integration event contracts v1 (additive changes only). Amounts are decimal strings. */
public class OutboxAccountEventPublisher implements AccountEventPublisher {

    private final OutboxWriter outbox;

    public OutboxAccountEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void accountOpened(Account a) {
        publish("account.account-opened", "account.AccountOpened", a.id(),
                new AccountOpened(a.id(), a.userId(), a.name(), a.type().name(), a.currency().getCurrencyCode(),
                        a.createdAt()));
    }

    @Override
    public void balanceChanged(Account a, BalanceEntry e) {
        publish("account.balance-changed", "account.BalanceChanged", a.id(),
                new BalanceChanged(a.id(), a.userId(), e.id(), e.kind().name(), e.amount().amount().toPlainString(),
                        e.balanceAfter().amount().toPlainString(), a.currency().getCurrencyCode(), e.referenceId(),
                        e.createdAt()));
    }

    private void publish(String routingKey, String type, UUID accountId, Object payload) {
        outbox.append(OutboxMessage.builder()
                .exchange(Exchanges.EVENTS).routingKey(routingKey).type(type)
                .aggregate("Account", accountId.toString())
                .payload(payload)
                .build());
    }

    record AccountOpened(UUID accountId, UUID userId, String name, String type, String currency, Instant openedAt) {}

    record BalanceChanged(UUID accountId, UUID userId, UUID entryId, String kind, String amount, String balanceAfter,
                          String currency, UUID referenceId, Instant occurredAt) {}
}
