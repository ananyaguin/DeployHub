package com.cloudflow.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkerNameRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = InputPatterns.WORKER_NAME) String name,
        @Min(1) @Max(16_000) Integer cpuMillis,
        @Min(1) @Max(65_536) Integer memoryMb) {}
