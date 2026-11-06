package com.budgetflow.budget.config;

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
}
