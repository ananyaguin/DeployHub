package com.cloudflow.controller;

import java.util.UUID;

import com.cloudflow.dto.request.CreateDeploymentRequest;
import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.request.UpdateDeploymentRequest;
import com.cloudflow.dto.response.DeploymentResponse;
import com.cloudflow.dto.response.DeploymentVersionResponse;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentController {

    private final DeploymentService deployments;

    public DeploymentController(DeploymentService deployments) {
        this.deployments = deployments;
    }

    @PostMapping("/api/v1/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentResponse create(@Valid @RequestBody CreateDeploymentRequest request) {
        return deployments.create(request);
    }

    @GetMapping("/api/v1/deployments")
    public PageResponse<DeploymentResponse> list(@Valid PageQuery page) {
        return deployments.list(page);
    }

    @PatchMapping("/api/v1/deployments/{publicId}")
    public DeploymentResponse update(
            @PathVariable UUID publicId, @Valid @RequestBody UpdateDeploymentRequest request) {
        return deployments.update(publicId, request);
    }

    @PostMapping("/api/v1/deployments/{publicId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentVersionResponse addVersion(@PathVariable UUID publicId) {
        return deployments.addVersion(publicId);
    }

    @GetMapping("/api/v1/deployments/{publicId}/versions")
    public PageResponse<DeploymentVersionResponse> versions(@PathVariable UUID publicId, @Valid PageQuery page) {
        return deployments.versions(publicId, page);
    }

    @PostMapping("/api/v1/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse execute(@Valid @RequestBody CreateExecutionRequest request) {
        return deployments.execute(request);
    }
}
