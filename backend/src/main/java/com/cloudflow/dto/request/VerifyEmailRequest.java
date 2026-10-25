package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(@NotBlank @Size(min = 32, max = 256) String token) {}
