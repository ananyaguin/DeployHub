package com.cloudflow.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflow.worker")
public record WorkerAgentProperties(
        String backendUrl,
        String token,
        String name,
        Duration pollInterval,
        String executor,
        String dockerHost,
        int memoryMb,
        int cpuMillis) {

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
        if (executor == null || executor.isBlank()) {
            executor = "simulator";
        }
        if (!executor.equals("simulator") && !executor.equals("docker")) {
            throw new IllegalStateException("WORKER_EXECUTOR must be simulator or docker");
        }
        dockerHost = dockerHost == null ? "" : dockerHost;
        if (memoryMb < 32 || memoryMb > 512) {
            throw new IllegalStateException("DOCKER_MEMORY_MB must be from 32 to 512");
        }
        if (cpuMillis < 100 || cpuMillis > 1000) {
            throw new IllegalStateException("DOCKER_CPU_MILLIS must be from 100 to 1000");
        }
        if (executor.equals("docker")
                && !dockerHost.startsWith("unix://")
                && !dockerHost.startsWith("tcp://")) {
            throw new IllegalStateException("DOCKER_HOST is required for Docker execution");
        }
    }

    public long memoryBytes() {
        return memoryMb * 1024L * 1024L;
    }

    public long nanoCpus() {
        return cpuMillis * 1_000_000L;
    }
}
