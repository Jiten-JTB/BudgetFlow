package com.budgetflow.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Real Postgres + RabbitMQ for service integration tests. {@code @Import} it on an abstract IT base class. */
@TestConfiguration(proxyBeanMethods = false)
public class ServiceContainers {

    public static final String POSTGRES_IMAGE = "postgres:17-alpine";
    public static final String RABBITMQ_IMAGE = "rabbitmq:4-management-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer(RABBITMQ_IMAGE);
    }
}
