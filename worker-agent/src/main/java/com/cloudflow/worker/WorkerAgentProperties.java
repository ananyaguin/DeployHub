package com.cloudflow.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflow.worker")
public record WorkerAgentProperties(String backendUrl, String token, String name, Duration pollInterval) {

    public WorkerAgentProperties {
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException("WORKER_BACKEND_URL is required");
        }
        backendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        token = token == null ? "" : token;
        if (name == null || !name.matches("^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$")) {
            throw new IllegalStateException("WORKER_NAME is invalid");
        }
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalStateException("WORKER_POLL_INTERVAL must be positive");
        }
    }
}
