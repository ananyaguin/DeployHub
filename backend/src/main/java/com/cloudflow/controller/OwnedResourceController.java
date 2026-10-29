package com.cloudflow.controller;

import java.util.UUID;

import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.response.DeploymentEventResponse;
import com.cloudflow.dto.response.DeploymentHealthResponse;
import com.cloudflow.dto.response.DeploymentResponse;
import com.cloudflow.dto.response.DeploymentVersionResponse;
import com.cloudflow.dto.response.ExecutionLogResponse;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.NotificationDeliveryResponse;
import com.cloudflow.dto.response.NotificationPreferenceResponse;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.service.OwnedResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OwnedResourceController {

    private final OwnedResourceService resources;

    public OwnedResourceController(OwnedResourceService resources) {
        this.resources = resources;
    }

    @GetMapping("/api/v1/deployments/{publicId}")
    public DeploymentResponse deployment(@PathVariable UUID publicId) {
        return resources.deployment(publicId);
    }

    @GetMapping("/api/v1/deployments/{publicId}/health")
    public DeploymentHealthResponse health(@PathVariable UUID publicId) {
        return resources.health(publicId);
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/versions/{versionId}")
    public DeploymentVersionResponse version(@PathVariable UUID deploymentId, @PathVariable UUID versionId) {
        return resources.version(deploymentId, versionId);
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/events")
    public PageResponse<DeploymentEventResponse> events(@PathVariable UUID deploymentId, @Valid PageQuery page) {
        return resources.events(deploymentId, page);
    }

    @GetMapping("/api/v1/audit")
    public PageResponse<DeploymentEventResponse> audit(@Valid PageQuery page) {
        return resources.audit(page);
    }

    @GetMapping("/api/v1/deployments/{deploymentId}/executions")
    public PageResponse<ExecutionResponse> executions(@PathVariable UUID deploymentId, @Valid PageQuery page) {
        return resources.executions(deploymentId, page);
    }

    @GetMapping("/api/v1/executions/{publicId}")
    public ExecutionResponse execution(@PathVariable UUID publicId) {
        return resources.execution(publicId);
    }

    @GetMapping("/api/v1/executions/{executionId}/logs")
    public PageResponse<ExecutionLogResponse> logs(@PathVariable UUID executionId, @Valid PageQuery page) {
        return resources.logs(executionId, page);
    }

    @GetMapping("/api/v1/notification-preference")
    public NotificationPreferenceResponse preference() {
        return resources.preference();
    }

    @GetMapping("/api/v1/notification-deliveries")
    public PageResponse<NotificationDeliveryResponse> deliveries(@Valid PageQuery page) {
        return resources.deliveries(page);
    }
}
