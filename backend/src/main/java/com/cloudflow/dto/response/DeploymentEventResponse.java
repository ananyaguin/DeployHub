package com.cloudflow.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeploymentEventResponse(
        String eventType,
        String message,
        Instant createdAt,
        String actor,
        UUID deploymentId,
        UUID executionId) {}
