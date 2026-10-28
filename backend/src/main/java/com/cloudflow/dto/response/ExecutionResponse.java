package com.cloudflow.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.TaskType;

public record ExecutionResponse(
        UUID publicId,
        UUID deploymentId,
        UUID versionId,
        UUID workerId,
        TaskType taskType,
        ExecutionStatus status,
        int attemptCount,
        Instant startedAt,
        Instant finishedAt) {}
