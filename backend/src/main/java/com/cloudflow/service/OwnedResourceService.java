package com.cloudflow.service;

import java.util.UUID;

import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.response.DeploymentEventResponse;
import com.cloudflow.dto.response.DeploymentResponse;
import com.cloudflow.dto.response.DeploymentVersionResponse;
import com.cloudflow.dto.response.ExecutionLogResponse;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.NotificationDeliveryResponse;
import com.cloudflow.dto.response.NotificationPreferenceResponse;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.entity.Deployment;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionLog;
import com.cloudflow.entity.NotificationDelivery;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.ExecutionLogRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.NotificationDeliveryRepository;
import com.cloudflow.repository.NotificationPreferenceRepository;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.security.CurrentUserAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnedResourceService {

    private final CurrentUserAccessor currentUser;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentEventRepository events;
    private final ExecutionRepository executions;
    private final ExecutionLogRepository logs;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;

    public OwnedResourceService(
            CurrentUserAccessor currentUser,
            DeploymentRepository deployments,
            DeploymentVersionRepository versions,
            DeploymentEventRepository events,
            ExecutionRepository executions,
            ExecutionLogRepository logs,
            NotificationPreferenceRepository preferences,
            NotificationDeliveryRepository deliveries) {
        this.currentUser = currentUser;
        this.deployments = deployments;
        this.versions = versions;
        this.events = events;
        this.executions = executions;
        this.logs = logs;
        this.preferences = preferences;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public DeploymentResponse deployment(UUID publicId) {
        return deploymentResponse(requireDeployment(publicId));
    }

    @Transactional(readOnly = true)
    public DeploymentVersionResponse version(UUID deploymentId, UUID versionId) {
        AuthenticatedUser caller = currentUser.require();
        DeploymentVersion version = versions
                .findByPublicIdAndDeployment_Tenant_Id(versionId, caller.tenantKey())
                .filter(found -> deploymentId.equals(found.getDeployment().getPublicId()))
                .orElseThrow(NotFoundException::new);
        return new DeploymentVersionResponse(version.getPublicId(), version.getVersionNumber());
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentEventResponse> events(UUID deploymentId, PageQuery page) {
        requireDeployment(deploymentId);
        AuthenticatedUser caller = currentUser.require();
        Page<DeploymentEvent> results = events.findByDeployment_Tenant_IdAndDeployment_PublicId(
                caller.tenantKey(), deploymentId, PageRequest.of(page.page(), page.size()));
        return new PageResponse<>(
                results.stream()
                        .map(event -> new DeploymentEventResponse(
                                event.getEventType(), event.getMessage(), event.getCreatedAt()))
                        .toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ExecutionResponse execution(UUID publicId) {
        Execution execution = requireExecution(publicId);
        UUID workerId = execution.getWorkerNode() == null ? null : execution.getWorkerNode().getPublicId();
        return new ExecutionResponse(
                execution.getPublicId(),
                execution.getDeployment().getPublicId(),
                execution.getDeploymentVersion().getPublicId(),
                workerId,
                execution.getTaskType(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionLogResponse> logs(UUID executionId, PageQuery page) {
        requireExecution(executionId);
        AuthenticatedUser caller = currentUser.require();
        Page<ExecutionLog> results = logs.findByExecution_PublicIdAndExecution_Deployment_Tenant_Id(
                executionId, caller.tenantKey(), PageRequest.of(page.page(), page.size()));
        return new PageResponse<>(
                results.stream()
                        .map(entry -> new ExecutionLogResponse(
                                entry.getSequenceNumber(), entry.getLevel(), entry.getMessage()))
                        .toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse preference() {
        AuthenticatedUser caller = currentUser.require();
        return preferences
                .findByUser_PublicIdAndUser_Tenant_Id(caller.userId(), caller.tenantKey())
                .map(preference -> new NotificationPreferenceResponse(preference.isEmailEnabled()))
                .orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDeliveryResponse> deliveries(PageQuery page) {
        AuthenticatedUser caller = currentUser.require();
        Page<NotificationDelivery> results = deliveries.findByUser_Tenant_IdAndUser_PublicId(
                caller.tenantKey(), caller.userId(), PageRequest.of(page.page(), page.size()));
        return new PageResponse<>(
                results.stream()
                        .map(delivery -> new NotificationDeliveryResponse(
                                delivery.getChannel(), delivery.getStatus(), delivery.getSentAt()))
                        .toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    private Deployment requireDeployment(UUID publicId) {
        AuthenticatedUser caller = currentUser.require();
        return deployments
                .findByPublicIdAndTenant_Id(publicId, caller.tenantKey())
                .orElseThrow(NotFoundException::new);
    }

    private Execution requireExecution(UUID publicId) {
        AuthenticatedUser caller = currentUser.require();
        return executions
                .findByPublicIdAndDeployment_Tenant_Id(publicId, caller.tenantKey())
                .orElseThrow(NotFoundException::new);
    }

    private static DeploymentResponse deploymentResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getPublicId(),
                deployment.getName(),
                deployment.getSlug(),
                deployment.getStatus(),
                deployment.getCurrentVersionNumber());
    }
}
