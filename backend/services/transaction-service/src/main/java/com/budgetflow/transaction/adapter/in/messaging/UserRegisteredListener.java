package com.budgetflow.transaction.adapter.in.messaging;

import com.budgetflow.transaction.application.CategoryService;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** identity.UserRegistered -> seed the default income and expense categories. */
public class UserRegisteredListener {

    public static final String QUEUE = "transaction-service.user-registered";
    static final String CONSUMER = "transaction.user-registered";

    private final IdempotentConsumer inbox;
    private final CategoryService categories;
    private final JsonMapper json;

    public UserRegisteredListener(IdempotentConsumer inbox, CategoryService categories, JsonMapper json) {
        this.inbox = inbox;
        this.categories = categories;
        this.json = json;
    }

    @RabbitListener(queues = QUEUE)
    public void onUserRegistered(Message message) {
        UUID messageId = QueueTopology.messageId(message);
        JsonNode event = json.readTree(message.getBody());
        UUID userId = UUID.fromString(event.get("userId").asString());
        inbox.handle(messageId, CONSUMER, () -> categories.seedDefaults(userId));
    }
}
