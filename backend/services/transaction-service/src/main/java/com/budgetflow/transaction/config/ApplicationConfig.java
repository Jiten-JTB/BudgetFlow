package com.budgetflow.transaction.config;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import java.util.List;
import com.budgetflow.common.messaging.erasure.UserDataEraser;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import com.budgetflow.transaction.adapter.in.messaging.SagaCommandListener;
import com.budgetflow.transaction.adapter.in.messaging.UserRegisteredListener;
import com.budgetflow.transaction.adapter.out.messaging.OutboxTransactionEventPublisher;
import com.budgetflow.transaction.adapter.out.persistence.JdbcCategoryRepository;
import com.budgetflow.transaction.adapter.out.persistence.JdbcIncomeSourceRepository;
import com.budgetflow.transaction.adapter.out.persistence.JdbcTransactionRepository;
import com.budgetflow.transaction.application.CategoryService;
import com.budgetflow.transaction.application.IncomeSourceService;
import com.budgetflow.transaction.application.TransactionService;
import com.budgetflow.transaction.application.port.CategoryRepository;
import com.budgetflow.transaction.application.port.IncomeSourceRepository;
import java.time.Clock;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    static final String USER_DELETED_QUEUE = "transaction-service.user-deleted";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CategoryRepository categoryRepository(JdbcClient jdbc) {
        return new JdbcCategoryRepository(jdbc);
    }

    @Bean
    IncomeSourceRepository incomeSourceRepository(JdbcClient jdbc) {
        return new JdbcIncomeSourceRepository(jdbc);
    }

    @Bean
    CategoryService categoryService(CategoryRepository categories, Clock clock) {
        return new CategoryService(categories, clock);
    }

    @Bean
    IncomeSourceService incomeSourceService(IncomeSourceRepository sources, Clock clock) {
        return new IncomeSourceService(sources, clock);
    }

    @Bean
    TransactionService transactionService(JdbcClient jdbc, CategoryRepository categories,
                                          IncomeSourceRepository sources, OutboxWriter outbox, Clock clock) {
        return new TransactionService(new JdbcTransactionRepository(jdbc), categories, sources,
                new OutboxTransactionEventPublisher(outbox), clock);
    }

    @Bean
    Declarables sagaCommandTopology() {
        return QueueTopology.subscribe(SagaCommandListener.QUEUE, Exchanges.COMMANDS, SagaCommandListener.ROUTING_PATTERN);
    }

    @Bean
    SagaCommandListener sagaCommandListener(SagaParticipantFactory participants, TransactionService transactions) {
        return new SagaCommandListener(participants, transactions);
    }

    @Bean
    Declarables userRegisteredTopology() {
        return QueueTopology.subscribe(UserRegisteredListener.QUEUE, Exchanges.EVENTS, "identity.user-registered");
    }

    @Bean
    UserRegisteredListener userRegisteredListener(IdempotentConsumer inbox, CategoryService categories, JsonMapper json) {
        return new UserRegisteredListener(inbox, categories, json);
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
        return new UserDataEraser(USER_DELETED_QUEUE, "transaction.user-deleted", inbox, jdbc, List.of(
                "DELETE FROM money_transaction WHERE user_id = :userId",
                "DELETE FROM income_source WHERE user_id = :userId",
                "DELETE FROM category WHERE user_id = :userId"));
    }

}
