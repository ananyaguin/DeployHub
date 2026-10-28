package com.cloudflow.dto.response;

import java.time.Instant;

import com.cloudflow.entity.HealthStatus;

public record DeploymentHealthResponse(
        HealthStatus status,
        Instant checkedAt,
        String detail,
        int failureStreak,
        boolean workerAvailable,
        boolean simulated) {}
