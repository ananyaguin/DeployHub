package com.cloudflow.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.cloudflow.entity.WorkerStatus;

public record WorkerNodeResponse(UUID publicId, String name, WorkerStatus status, Instant lastSeenAt) {}
