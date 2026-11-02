package com.budgetflow.common.messaging.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("budgetflow.messaging")
public record MessagingProperties(Outbox outbox) {

    public MessagingProperties {
        outbox = outbox == null ? new Outbox(true, 100, 10, Duration.ofMillis(500)) : outbox;
    }

    public record Outbox(Boolean relayEnabled, Integer batchSize, Integer maxAttempts, Duration pollInterval) {
        public Outbox {
            relayEnabled = relayEnabled == null || relayEnabled;
            batchSize = batchSize == null ? 100 : batchSize;
            maxAttempts = maxAttempts == null ? 10 : maxAttempts;
            pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        }
    }
}
