package com.budgetflow.orchestrator.config;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import java.util.List;
import com.budgetflow.common.messaging.erasure.UserDataEraser;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.common.messaging.saga.SagaContract;
import com.budgetflow.orchestrator.adapter.in.messaging.SagaReplyListener;
import com.budgetflow.orchestrator.adapter.in.messaging.TransactionRequestedListener;
import com.budgetflow.orchestrator.adapter.out.messaging.OutboxCommandBus;
import com.budgetflow.orchestrator.adapter.out.persistence.JdbcSagaRepository;
import com.budgetflow.orchestrator.application.SagaCoordinator;
import com.budgetflow.orchestrator.domain.SagaPolicy;
import java.time.Clock;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    static final String USER_DELETED_QUEUE = "orchestrator-service.user-deleted";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SagaCoordinator sagaCoordinator(JdbcClient jdbc, JsonMapper json, OutboxWriter outbox,
                                    OrchestratorProperties properties, Clock clock) {
        return new SagaCoordinator(new JdbcSagaRepository(jdbc, json), new OutboxCommandBus(outbox),
                new SagaPolicy(properties.stepTimeout(), properties.maxAttempts()), clock);
    }

    @Bean
    SagaTimeoutScheduler sagaTimeoutScheduler(SagaCoordinator coordinator, OrchestratorProperties properties) {
        return new SagaTimeoutScheduler(coordinator, properties);
    }

    @Bean
    Declarables transactionRequestedTopology() {
        return QueueTopology.subscribe(TransactionRequestedListener.QUEUE, Exchanges.EVENTS,
                TransactionRequestedListener.ROUTING_KEY);
    }

    @Bean
    Declarables sagaReplyTopology() {
        return QueueTopology.subscribe(SagaReplyListener.QUEUE, Exchanges.REPLIES, SagaContract.REPLY_ROUTING_KEY);
    }

    @Bean
    TransactionRequestedListener transactionRequestedListener(IdempotentConsumer inbox, SagaCoordinator coordinator,
                                                              JsonMapper json) {
        return new TransactionRequestedListener(inbox, coordinator, json);
    }

    @Bean
    SagaReplyListener sagaReplyListener(IdempotentConsumer inbox, SagaCoordinator coordinator, JsonMapper json) {
        return new SagaReplyListener(inbox, coordinator, json);
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
        return new UserDataEraser(USER_DELETED_QUEUE, "orchestrator.user-deleted", inbox, jdbc, List.of(
                "DELETE FROM saga_event WHERE saga_id IN (SELECT id FROM saga_instance WHERE user_id = :userId)",
                "DELETE FROM saga_instance WHERE user_id = :userId"));
    }

}
