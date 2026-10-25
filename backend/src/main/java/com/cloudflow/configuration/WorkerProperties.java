package com.cloudflow.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflow.worker")
public record WorkerProperties(Duration heartbeatTimeout, Duration runningTimeout, int maxAttempts) {

    public WorkerProperties {
        if (heartbeatTimeout == null || heartbeatTimeout.isZero() || heartbeatTimeout.isNegative()) {
            throw new IllegalStateException("WORKER_HEARTBEAT_TIMEOUT must be positive");
        }
        if (runningTimeout == null || runningTimeout.isZero() || runningTimeout.isNegative()) {
            throw new IllegalStateException("WORKER_RUNNING_TIMEOUT must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException("WORKER_MAX_ATTEMPTS must be at least 1");
        }
    }
}
