package com.cloudflow.dto.response;

import java.time.Instant;

public record DeploymentEventResponse(String eventType, String message, Instant createdAt) {}
