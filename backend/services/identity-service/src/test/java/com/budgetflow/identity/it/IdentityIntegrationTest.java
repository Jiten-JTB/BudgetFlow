package com.budgetflow.identity.it;

import com.budgetflow.identity.IdentityServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Shared Spring context (cached across ITs) against real Postgres and RabbitMQ. */
@SpringBootTest(classes = IdentityServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityIntegrationTest.Containers.class)
public abstract class IdentityIntegrationTest {

    public static final String REGISTER_JSON = """
            {"email":"%s","password":"correct horse battery","displayName":"Alice",
             "baseCurrency":"INR","timezone":"Asia/Kolkata"}""";

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbit() {
            return new RabbitMQContainer("rabbitmq:4-management-alpine");
        }
    }

    protected static String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@example.com";
    }
}
