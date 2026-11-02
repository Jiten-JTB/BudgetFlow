package com.budgetflow.common.messaging.config;

import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxRelay;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    Declarables budgetflowExchanges() {
        return new Declarables(
                new TopicExchange(Exchanges.COMMANDS),
                new TopicExchange(Exchanges.REPLIES),
                new TopicExchange(Exchanges.EVENTS));
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(JdbcClient jdbc, JsonMapper json) {
        return new OutboxWriter(jdbc, json);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRelay outboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx, MessagingProperties props) {
        return new OutboxRelay(jdbc, rabbit, tx, props.outbox().batchSize(), props.outbox().maxAttempts());
    }

    @Bean
    @ConditionalOnMissingBean
    IdempotentConsumer idempotentConsumer(JdbcClient jdbc, TransactionTemplate tx) {
        return new IdempotentConsumer(jdbc, tx);
    }

    @Bean
    @ConditionalOnProperty(name = "budgetflow.messaging.outbox.relay-enabled", matchIfMissing = true)
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay, MessagingProperties props) {
        return new OutboxRelayScheduler(relay, props.outbox().pollInterval().toMillis());
    }

    /** Polls the outbox on a single virtual thread; drains immediately while full batches keep coming. */
    static final class OutboxRelayScheduler implements SmartLifecycle {

        private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

        private final OutboxRelay relay;
        private final long intervalMillis;
        private ScheduledExecutorService executor;

        OutboxRelayScheduler(OutboxRelay relay, long intervalMillis) {
            this.relay = relay;
            this.intervalMillis = intervalMillis;
        }

        @Override
        public synchronized void start() {
            executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("outbox-relay").factory());
            executor.scheduleWithFixedDelay(this::drain, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        }

        private void drain() {
            try {
                while (relay.publishPending() > 0) {
                    // keep draining until the outbox is empty or a publish fails
                }
            } catch (RuntimeException e) {
                log.error("Outbox relay run failed", e);
            }
        }

        @Override
        public synchronized void stop() {
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
        }

        @Override
        public synchronized boolean isRunning() {
            return executor != null;
        }
    }
}
