package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDeploymentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) @Pattern(regexp = InputPatterns.SLUG) String slug) {}
