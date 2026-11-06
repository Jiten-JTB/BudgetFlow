package com.budgetflow.orchestrator.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param stepTimeout         wait for a participant reply before timing out a step
 * @param maxAttempts         sends of one command (retriable steps, compensations) before giving up
 * @param timeoutScanInterval how often to look for timed-out sagas
 * @param timeoutBatchSize    sagas handled per scan
 */
@ConfigurationProperties("budgetflow.orchestrator")
public record OrchestratorProperties(Duration stepTimeout, Integer maxAttempts, Duration timeoutScanInterval,
                                     Integer timeoutBatchSize) {

    public OrchestratorProperties {
        stepTimeout = stepTimeout == null ? Duration.ofSeconds(30) : stepTimeout;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        timeoutScanInterval = timeoutScanInterval == null ? Duration.ofSeconds(1) : timeoutScanInterval;
        timeoutBatchSize = timeoutBatchSize == null ? 100 : timeoutBatchSize;
    }
}
