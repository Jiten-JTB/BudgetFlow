package com.budgetflow.budget.adapter.in.messaging;

import static com.budgetflow.common.messaging.saga.CommandFields.optionalUuid;
import static com.budgetflow.common.messaging.saga.CommandFields.text;
import static com.budgetflow.common.messaging.saga.CommandFields.uuid;

import com.budgetflow.budget.application.ConsumeBudgetCommand;
import com.budgetflow.budget.application.ConsumptionService;
import com.budgetflow.budget.application.Inputs;
import com.budgetflow.common.messaging.saga.CommandHandler;
import com.budgetflow.common.messaging.saga.SagaParticipant;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Saga participant: count an expense against budgets (consume) and undo it (release). */
public class SagaCommandListener {

    public static final String QUEUE = "budget-service.saga-commands";
    public static final String ROUTING_PATTERN = "budget.#";

    private final SagaParticipant participant;

    public SagaCommandListener(SagaParticipantFactory participants, ConsumptionService consumption) {
        this.participant = participants.create("budget-service", Map.<String, CommandHandler>of(
                "budget.ConsumeBudget", c -> consumption.consume(new ConsumeBudgetCommand(
                        uuid(c, "userId"), uuid(c, "transactionId"), optionalUuid(c, "categoryId"),
                        optionalUuid(c, "parentCategoryId"),
                        Inputs.money(text(c, "amount"), Inputs.currency(text(c, "currency"))),
                        Inputs.date(text(c, "occurredOn")), text(c, "operationKey"))),
                "budget.ReleaseBudget", c -> consumption.release(uuid(c, "userId"), text(c, "operationKey"))));
    }

    @RabbitListener(queues = QUEUE)
    public void onCommand(Message message) {
        participant.handle(message);
    }
}
