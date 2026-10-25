package com.cloudflow.dto.response;

import java.util.Map;
import java.util.UUID;

import com.cloudflow.entity.TaskType;

public record WorkerTaskResponse(
        UUID publicId,
        TaskType taskType,
        int attempt,
        UUID tenantId,
        UUID deploymentId,
        int versionNumber,
        String image,
        Map<String, String> environment,
        Integer currentVersion,
        String containerId,
        boolean configurationValid) {}
