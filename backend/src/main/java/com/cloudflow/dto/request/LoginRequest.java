package com.cloudflow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 63) @Pattern(regexp = InputPatterns.SLUG) String tenantSlug,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password) {}
