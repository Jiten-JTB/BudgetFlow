package com.budgetflow.transaction.adapter.in.messaging;

import static com.budgetflow.common.messaging.saga.CommandFields.optionalText;
import static com.budgetflow.common.messaging.saga.CommandFields.text;
import static com.budgetflow.common.messaging.saga.CommandFields.uuid;

import com.budgetflow.common.messaging.saga.CommandHandler;
import com.budgetflow.common.messaging.saga.SagaParticipant;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import com.budgetflow.transaction.application.TransactionService;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Saga participant: final step (complete) and failure step (cancel) of the RecordTransaction saga. */
public class SagaCommandListener {

    public static final String QUEUE = "transaction-service.saga-commands";
    public static final String ROUTING_PATTERN = "transaction.#";

    private final SagaParticipant participant;

    public SagaCommandListener(SagaParticipantFactory participants, TransactionService transactions) {
        this.participant = participants.create("transaction-service", Map.<String, CommandHandler>of(
                "transaction.CompleteTransaction", c -> transactions.complete(
                        uuid(c, "userId"), uuid(c, "transactionId"), uuid(c, "sagaId")),
                "transaction.CancelTransaction", c -> transactions.cancel(
                        uuid(c, "userId"), uuid(c, "transactionId"), text(c, "failureCode"),
                        optionalText(c, "failureReason"))));
    }

    @RabbitListener(queues = QUEUE)
    public void onCommand(Message message) {
        participant.handle(message);
    }
}
