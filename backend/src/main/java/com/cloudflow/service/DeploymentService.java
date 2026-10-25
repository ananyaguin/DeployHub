package com.cloudflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.cloudflow.dto.request.CreateDeploymentRequest;
import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.request.UpdateDeploymentRequest;
import com.cloudflow.dto.response.DeploymentResponse;
import com.cloudflow.dto.response.DeploymentVersionResponse;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.entity.Deployment;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.Tenant;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.exception.UnauthorizedException;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.TenantRepository;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.security.CurrentUserAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {

    private static final String STATE = "Deployment state cannot change that way";
    private static final String LOCAL = " No cloud provider was contacted.";

    private final CurrentUserAccessor currentUser;
    private final TenantRepository tenants;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentEventRepository events;
    private final ExecutionRepository executions;
    private final Clock clock;

    public DeploymentService(
            CurrentUserAccessor currentUser,
            TenantRepository tenants,
            DeploymentRepository deployments,
            DeploymentVersionRepository versions,
            DeploymentEventRepository events,
            ExecutionRepository executions,
            Clock clock) {
        this.currentUser = currentUser;
        this.tenants = tenants;
        this.deployments = deployments;
        this.versions = versions;
        this.events = events;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional
    public DeploymentResponse create(CreateDeploymentRequest request) {
        AuthenticatedUser caller = currentUser.require();
        Tenant tenant = tenants.findById(caller.tenantKey()).orElseThrow(UnauthorizedException::new);
        String slug = request.slug().trim();
        if (deployments.findByTenant_IdAndSlug(tenant.getId(), slug).isPresent()) {
            throw new ConflictException();
        }
        Deployment deployment = new Deployment();
        deployment.setTenant(tenant);
        deployment.setName(request.name().trim());
        deployment.setSlug(slug);
        deployment.setStatus(DeploymentStatus.DRAFT);
        deployment.assignIdentity();
        deployment = deployments.save(deployment);
        event(deployment, null, "CREATED", "Deployment created as DRAFT.");
        return response(deployment);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentResponse> list(PageQuery page) {
        AuthenticatedUser caller = currentUser.require();
        Page<Deployment> results = deployments.findByTenant_Id(
                caller.tenantKey(), PageRequest.of(page.page(), page.size(), Sort.by("id")));
        return new PageResponse<>(
                results.stream().map(DeploymentService::response).toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    @Transactional
    public DeploymentResponse update(UUID publicId, UpdateDeploymentRequest request) {
        Deployment deployment = requireDeployment(publicId);
        if (request.status() != deployment.getStatus()) {
            throw new ConflictException(STATE);
        }
        claim(deployment, deployment.getStatus(), deployment.getCurrentVersionNumber(), deployment.getStatus(), deployment.getCurrentVersionNumber());
        String name = request.name().trim();
        if (!name.equals(deployment.getName())) {
            deployment.setName(name);
            event(deployment, null, "RENAMED", "Name changed to " + name + ".");
        }
        return response(deployments.save(deployment));
    }

    @Transactional
    public DeploymentVersionResponse addVersion(UUID publicId) {
        Deployment deployment = requireDeployment(publicId);
        if (deployment.getStatus() == DeploymentStatus.ARCHIVED) {
            throw new ConflictException(STATE);
        }
        Integer max = versions.findMaxVersionNumber(deployment.getId());
        DeploymentVersion version = new DeploymentVersion();
        version.setDeployment(deployment);
        version.setVersionNumber(max == null ? 1 : max + 1);
        version.assignIdentity();
        version = versions.save(version);
        event(deployment, version, "VERSION_CREATED", "Version " + version.getVersionNumber() + " recorded.");
        return new DeploymentVersionResponse(version.getPublicId(), version.getVersionNumber());
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentVersionResponse> versions(UUID publicId, PageQuery page) {
        requireDeployment(publicId);
        AuthenticatedUser caller = currentUser.require();
        Page<DeploymentVersion> results = versions.findByDeployment_Tenant_IdAndDeployment_PublicId(
                caller.tenantKey(),
                publicId,
                PageRequest.of(page.page(), page.size(), Sort.by("versionNumber")));
        return new PageResponse<>(
                results.stream()
                        .map(version -> new DeploymentVersionResponse(version.getPublicId(), version.getVersionNumber()))
                        .toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    @Transactional
    public ExecutionResponse execute(CreateExecutionRequest request) {
        UUID deploymentId = UUID.fromString(request.deploymentId());
        Deployment deployment = requireDeployment(deploymentId);
        DeploymentVersion version = requireVersion(deploymentId, UUID.fromString(request.versionId()));
        return switch (request.taskType()) {
            case DEPLOY -> change(deployment, version, TaskType.DEPLOY);
            case ROLLBACK -> change(deployment, version, TaskType.ROLLBACK);
            case STOP -> change(deployment, version, TaskType.STOP);
        };
    }

    private ExecutionResponse change(Deployment deployment, DeploymentVersion version, TaskType task) {
        Integer current = deployment.getCurrentVersionNumber();
        DeploymentStatus next = switch (task) {
            case DEPLOY -> deploy(deployment, version, current);
            case ROLLBACK -> rollback(deployment, version, current);
            case STOP -> stop(deployment, version, current);
        };
        Integer nextVersion = task == TaskType.STOP ? current : version.getVersionNumber();
        claim(deployment, deployment.getStatus(), current, next, nextVersion);
        ExecutionResponse execution = record(deployment, version, task);
        event(
                deployment,
                version,
                task.name(),
                "Moved to " + next + " at version " + nextVersion + "." + LOCAL);
        return execution;
    }

    private static DeploymentStatus deploy(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() == DeploymentStatus.ARCHIVED
                || (current != null && version.getVersionNumber() <= current)) {
            throw new ConflictException(STATE);
        }
        return DeploymentStatus.ACTIVE;
    }

    private static DeploymentStatus rollback(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() != DeploymentStatus.ACTIVE
                || current == null
                || version.getVersionNumber() >= current) {
            throw new ConflictException(STATE);
        }
        return DeploymentStatus.ACTIVE;
    }

    private static DeploymentStatus stop(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() != DeploymentStatus.ACTIVE
                || current == null
                || !current.equals(version.getVersionNumber())) {
            throw new ConflictException(STATE);
        }
        return DeploymentStatus.ARCHIVED;
    }

    private void claim(
            Deployment deployment,
            DeploymentStatus expected,
            Integer expectedVersion,
            DeploymentStatus next,
            Integer nextVersion) {
        int rows = expectedVersion == null
                ? deployments.claimFromNew(deployment.getId(), expected, next, nextVersion)
                : deployments.claimFromVersion(deployment.getId(), expected, expectedVersion, next, nextVersion);
        if (rows != 1) {
            throw new ConflictException(STATE);
        }
        deployment.setStatus(next);
        deployment.setCurrentVersionNumber(nextVersion);
    }

    private ExecutionResponse record(Deployment deployment, DeploymentVersion version, TaskType task) {
        Instant now = clock.instant();
        Execution execution = new Execution();
        execution.setDeployment(deployment);
        execution.setDeploymentVersion(version);
        execution.setTaskType(task);
        execution.setStatus(ExecutionStatus.SUCCEEDED);
        execution.setStartedAt(now);
        execution.setFinishedAt(now);
        execution.assignIdentity();
        execution = executions.save(execution);
        return new ExecutionResponse(
                execution.getPublicId(),
                deployment.getPublicId(),
                version.getPublicId(),
                null,
                task,
                ExecutionStatus.SUCCEEDED,
                now,
                now);
    }

    private void event(Deployment deployment, DeploymentVersion version, String type, String message) {
        DeploymentEvent event = new DeploymentEvent();
        event.setDeployment(deployment);
        event.setDeploymentVersion(version);
        event.setEventType(type);
        event.setMessage(message);
        events.save(event);
    }

    private Deployment requireDeployment(UUID publicId) {
        AuthenticatedUser caller = currentUser.require();
        return deployments
                .findByPublicIdAndTenant_Id(publicId, caller.tenantKey())
                .orElseThrow(NotFoundException::new);
    }

    private DeploymentVersion requireVersion(UUID deploymentId, UUID versionId) {
        AuthenticatedUser caller = currentUser.require();
        return versions
                .findByPublicIdAndDeployment_Tenant_Id(versionId, caller.tenantKey())
                .filter(found -> deploymentId.equals(found.getDeployment().getPublicId()))
                .orElseThrow(NotFoundException::new);
    }

    private static DeploymentResponse response(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getPublicId(),
                deployment.getName(),
                deployment.getSlug(),
                deployment.getStatus(),
                deployment.getCurrentVersionNumber());
    }
}
