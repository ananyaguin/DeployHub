package com.cloudflow.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVersionRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = InputPatterns.IMAGE) String image,
        @Size(max = 20) Map<String, String> environment) {}
