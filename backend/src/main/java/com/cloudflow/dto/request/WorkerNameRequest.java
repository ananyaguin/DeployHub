package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkerNameRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = InputPatterns.WORKER_NAME) String name) {}
