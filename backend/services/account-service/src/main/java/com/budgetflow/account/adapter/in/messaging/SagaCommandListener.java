package com.budgetflow.account.adapter.in.messaging;

import static com.budgetflow.common.messaging.saga.CommandFields.optionalText;
import static com.budgetflow.common.messaging.saga.CommandFields.optionalUuid;
import static com.budgetflow.common.messaging.saga.CommandFields.text;
import static com.budgetflow.common.messaging.saga.CommandFields.uuid;

import com.budgetflow.account.application.ApplyBalanceChangeCommand;
import com.budgetflow.account.application.BalanceService;
import com.budgetflow.account.application.Inputs;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.messaging.saga.CommandHandler;
import com.budgetflow.common.messaging.saga.SagaParticipant;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.JsonNode;

/** Saga participant: move money (apply) and undo it (reverse). */
public class SagaCommandListener {

    public static final String QUEUE = "account-service.saga-commands";
    public static final String ROUTING_PATTERN = "account.#";

    private final SagaParticipant participant;

    public SagaCommandListener(SagaParticipantFactory participants, BalanceService balances) {
        this.participant = participants.create("account-service", Map.<String, CommandHandler>of(
                "account.ApplyBalanceChange", c -> balances.apply(applyCommand(c)),
                "account.ReverseBalanceChange", c -> balances.reverse(uuid(c, "userId"), text(c, "operationKey"))));
    }

    @RabbitListener(queues = QUEUE)
    public void onCommand(Message message) {
        participant.handle(message);
    }

    private static ApplyBalanceChangeCommand applyCommand(JsonNode c) {
        EntryKind kind;
        try {
            kind = EntryKind.valueOf(text(c, "entryKind"));
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Unknown entryKind", e);
        }
        return new ApplyBalanceChangeCommand(uuid(c, "userId"), uuid(c, "accountId"),
                Inputs.money(text(c, "amount"), Inputs.currency(text(c, "currency"))), kind, text(c, "operationKey"),
                optionalUuid(c, "referenceId"), optionalText(c, "description"));
    }
}
