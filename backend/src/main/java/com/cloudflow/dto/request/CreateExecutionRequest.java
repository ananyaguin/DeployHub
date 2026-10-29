package com.cloudflow.dto.request;

import com.cloudflow.entity.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateExecutionRequest(
        @NotBlank @Pattern(regexp = InputPatterns.UUID) String deploymentId,
        @NotBlank @Pattern(regexp = InputPatterns.UUID) String versionId,
        @NotNull TaskType taskType,
        @Min(1) @Max(16_000) Integer cpuMillis,
        @Min(1) @Max(65_536) Integer memoryMb) {}
