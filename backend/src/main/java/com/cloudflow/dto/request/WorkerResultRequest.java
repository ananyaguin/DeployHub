package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkerResultRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = InputPatterns.WORKER_NAME) String name,
        @NotNull Boolean succeeded,
        @Size(max = 1000) String message,
        @Pattern(regexp = InputPatterns.CONTAINER_ID) String containerId) {}
