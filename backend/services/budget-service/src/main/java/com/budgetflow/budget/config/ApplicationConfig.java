package com.budgetflow.budget.config;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import java.util.List;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.erasure.UserDataEraser;
import com.budgetflow.budget.adapter.in.messaging.SagaCommandListener;
import com.budgetflow.budget.adapter.out.messaging.OutboxBudgetEventPublisher;
import com.budgetflow.budget.adapter.out.persistence.JdbcBudgetRepository;
import com.budgetflow.budget.adapter.out.persistence.JdbcConsumptionRepository;
import com.budgetflow.budget.adapter.out.persistence.JdbcOperationLog;
import com.budgetflow.budget.adapter.out.persistence.JdbcUsageRepository;
import com.budgetflow.budget.application.BudgetService;
import com.budgetflow.budget.application.ConsumptionService;
import com.budgetflow.budget.application.port.BudgetRepository;
import com.budgetflow.budget.application.port.UsageRepository;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import org.springframework.amqp.core.Declarables;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    static final String USER_DELETED_QUEUE = "budget-service.user-deleted";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BudgetRepository budgetRepository(JdbcClient jdbc) {
        return new JdbcBudgetRepository(jdbc);
    }

    @Bean
    UsageRepository usageRepository(JdbcClient jdbc) {
        return new JdbcUsageRepository(jdbc);
    }

    @Bean
    BudgetService budgetService(BudgetRepository budgets, UsageRepository usages, Clock clock) {
        return new BudgetService(budgets, usages, clock);
    }

    @Bean
    Declarables sagaCommandTopology() {
        return QueueTopology.subscribe(SagaCommandListener.QUEUE, Exchanges.COMMANDS, SagaCommandListener.ROUTING_PATTERN);
    }

    @Bean
    SagaCommandListener sagaCommandListener(SagaParticipantFactory participants, ConsumptionService consumption) {
        return new SagaCommandListener(participants, consumption);
    }

    @Bean
    ConsumptionService consumptionService(BudgetRepository budgets, UsageRepository usages, JdbcClient jdbc,
                                          OutboxWriter outbox, BudgetService budgetService, Clock clock) {
        return new ConsumptionService(budgets, usages, new JdbcConsumptionRepository(jdbc), new JdbcOperationLog(jdbc),
                new OutboxBudgetEventPublisher(outbox), budgetService, clock);
    }

    /** identity.UserDeleted: erase every row this service keeps for the user (children before parents). */
    @Bean
    Declarables userDeletedTopology() {
        return UserDataEraser.topology(USER_DELETED_QUEUE);
    }

    @Bean
    SimpleMessageListenerContainer userDeletedListener(UserDataEraser eraser, SimpleRabbitListenerContainerFactory factory) {
        return eraser.container(factory);
    }

    @Bean
    UserDataEraser userDataEraser(IdempotentConsumer inbox, JdbcClient jdbc) {
        return new UserDataEraser(USER_DELETED_QUEUE, "budget.user-deleted", inbox, jdbc, List.of(
                "DELETE FROM budget_consumption WHERE user_id = :userId",
                "DELETE FROM budget_operation WHERE user_id = :userId",
                "DELETE FROM budget_period_usage WHERE budget_id IN (SELECT id FROM budget WHERE user_id = :userId)",
                "DELETE FROM budget WHERE user_id = :userId"));
    }

}
