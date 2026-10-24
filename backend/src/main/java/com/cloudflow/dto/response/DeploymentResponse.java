package com.cloudflow.dto.response;

import java.util.UUID;

import com.cloudflow.entity.DeploymentStatus;

public record DeploymentResponse(UUID publicId, String name, String slug, DeploymentStatus status) {}
