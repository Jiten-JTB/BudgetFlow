package com.budgetflow.identity.it;

import com.budgetflow.identity.IdentityServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
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

    /** Started before any context: mail auto-configuration needs spring.mail.host when it is evaluated. */
    protected static final Mailpit MAILPIT = new Mailpit();

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

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

    /** Real SMTP server with an HTTP API, so tests read the email a person would get. */
    public static class Mailpit extends GenericContainer<Mailpit> {
        Mailpit() {
            super("axllent/mailpit:v1.21.8");
            withExposedPorts(1025, 8025);
        }

        public String apiUrl() {
            return "http://" + getHost() + ":" + getMappedPort(8025);
        }
    }

    /** For tests about something other than verification: confirm the email as the link would. */
    protected static void markEmailVerified(org.springframework.jdbc.core.simple.JdbcClient jdbc, String email) {
        jdbc.sql("UPDATE users SET email_verified_at = now() WHERE email = :email").param("email", email).update();
    }

    protected static String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@example.com";
    }
}
