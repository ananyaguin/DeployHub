package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 63) @Pattern(regexp = InputPatterns.SLUG) String slug) {}
