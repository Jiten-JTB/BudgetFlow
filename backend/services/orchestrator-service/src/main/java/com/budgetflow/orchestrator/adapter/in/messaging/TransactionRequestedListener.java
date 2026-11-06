package com.budgetflow.orchestrator.adapter.in.messaging;

import static com.budgetflow.common.messaging.saga.CommandFields.optionalUuid;
import static com.budgetflow.common.messaging.saga.CommandFields.text;
import static com.budgetflow.common.messaging.saga.CommandFields.uuid;

import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.orchestrator.application.SagaCoordinator;
import com.budgetflow.orchestrator.domain.TransactionSagaData;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** transaction.TransactionRequested -> start the saga that applies it. */
public class TransactionRequestedListener {

    public static final String QUEUE = "orchestrator-service.transaction-requested";
    public static final String ROUTING_KEY = "transaction.transaction-requested";

    private final IdempotentConsumer inbox;
    private final SagaCoordinator coordinator;
    private final JsonMapper json;

    public TransactionRequestedListener(IdempotentConsumer inbox, SagaCoordinator coordinator, JsonMapper json) {
        this.inbox = inbox;
        this.coordinator = coordinator;
        this.json = json;
    }

    @RabbitListener(queues = QUEUE)
    public void onTransactionRequested(Message message) {
        JsonNode e = json.readTree(message.getBody());
        TransactionSagaData data = new TransactionSagaData(uuid(e, "transactionId"), uuid(e, "userId"), text(e, "kind"),
                uuid(e, "accountId"), optionalUuid(e, "counterAccountId"), text(e, "amount"), text(e, "currency"),
                optionalUuid(e, "categoryId"), optionalUuid(e, "parentCategoryId"), text(e, "occurredOn"));
        inbox.handle(QueueTopology.messageId(message), "orchestrator.transaction-requested",
                () -> coordinator.startForTransaction(data));
    }
}
