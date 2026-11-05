package com.budgetflow.common.messaging.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Messaging defaults (publisher confirms, consumer retry + dead-lettering); lowest precedence so services can override. */
public class MessagingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("budgetflowMessagingDefaults", Map.of(
                "spring.rabbitmq.publisher-confirm-type", "simple",
                // Consumers: bounded exponential retry, then dead-letter (never requeue forever).
                "spring.rabbitmq.listener.simple.retry.enabled", "true",
                "spring.rabbitmq.listener.simple.retry.max-retries", "4",          // 1 attempt + 4 retries
                "spring.rabbitmq.listener.simple.retry.initial-interval", "1s",
                "spring.rabbitmq.listener.simple.retry.multiplier", "2",
                "spring.rabbitmq.listener.simple.retry.max-interval", "30s",
                "spring.rabbitmq.listener.simple.default-requeue-rejected", "false")));
    }
}
