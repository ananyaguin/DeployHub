package com.cloudflow.dto.request;

import com.cloudflow.entity.DeploymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDeploymentRequest(
        @NotBlank @Size(max = 120) String name, @NotNull DeploymentStatus status) {}
