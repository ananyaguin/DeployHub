package com.cloudflow.dto.response;

import java.util.UUID;

import com.cloudflow.entity.TaskType;

public record WorkerTaskResponse(UUID publicId, TaskType taskType, int attempt) {}
