package com.budgetflow.common.messaging.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Defaults required by the outbox relay; lowest precedence so services can still override. */
public class MessagingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("budgetflowMessagingDefaults", Map.of(
                "spring.rabbitmq.publisher-confirm-type", "simple")));
    }
}
