package com.cloudflow.dto.response;

import java.util.UUID;

public record DeploymentVersionResponse(UUID publicId, int versionNumber, String image, String containerId) {}
