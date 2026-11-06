package com.budgetflow.orchestrator.config;

import com.budgetflow.orchestrator.application.SagaCoordinator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Scans for timed-out sagas on a single virtual thread; drains while full batches keep coming. */
class SagaTimeoutScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutScheduler.class);

    private final SagaCoordinator coordinator;
    private final OrchestratorProperties properties;
    private ScheduledExecutorService executor;

    SagaTimeoutScheduler(SagaCoordinator coordinator, OrchestratorProperties properties) {
        this.coordinator = coordinator;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        long interval = properties.timeoutScanInterval().toMillis();
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("saga-timeouts").factory());
        executor.scheduleWithFixedDelay(this::scan, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void scan() {
        try {
            while (coordinator.processTimeouts(properties.timeoutBatchSize()) == properties.timeoutBatchSize()) {
                // keep draining
            }
        } catch (RuntimeException e) {
            log.error("Saga timeout scan failed", e);
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
