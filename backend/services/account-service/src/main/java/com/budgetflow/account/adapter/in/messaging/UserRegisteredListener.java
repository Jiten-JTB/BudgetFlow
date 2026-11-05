package com.budgetflow.account.adapter.in.messaging;

import com.budgetflow.account.application.AccountService;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** identity.UserRegistered -> give every new user a "Cash" account in their base currency. */
public class UserRegisteredListener {

    public static final String QUEUE = "account-service.user-registered";
    static final String CONSUMER = "account.user-registered";

    private final IdempotentConsumer inbox;
    private final AccountService accounts;
    private final JsonMapper json;

    public UserRegisteredListener(IdempotentConsumer inbox, AccountService accounts, JsonMapper json) {
        this.inbox = inbox;
        this.accounts = accounts;
        this.json = json;
    }

    @RabbitListener(queues = QUEUE)
    public void onUserRegistered(Message message) {
        UUID messageId = QueueTopology.messageId(message);
        JsonNode event = json.readTree(message.getBody());
        UUID userId = UUID.fromString(event.get("userId").asString());
        String currency = event.get("baseCurrency").asString();
        inbox.handle(messageId, CONSUMER, () -> accounts.openDefaultCashAccount(userId, currency));
    }
}
