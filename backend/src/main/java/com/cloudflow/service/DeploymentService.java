package com.cloudflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
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
import com.cloudflow.entity.ExecutionLog;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.LogLevel;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.Tenant;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.exception.UnauthorizedException;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.ExecutionLogRepository;
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
    private static final Collection<ExecutionStatus> OPEN =
            EnumSet.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING);

    private final CurrentUserAccessor currentUser;
    private final TenantRepository tenants;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentEventRepository events;
    private final ExecutionRepository executions;
    private final ExecutionLogRepository logs;
    private final Clock clock;

    public DeploymentService(
            CurrentUserAccessor currentUser,
            TenantRepository tenants,
            DeploymentRepository deployments,
            DeploymentVersionRepository versions,
            DeploymentEventRepository events,
            ExecutionRepository executions,
            ExecutionLogRepository logs,
            Clock clock) {
        this.currentUser = currentUser;
        this.tenants = tenants;
        this.deployments = deployments;
        this.versions = versions;
        this.events = events;
        this.executions = executions;
        this.logs = logs;
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
        switch (task) {
            case DEPLOY -> deploy(deployment, version, current);
            case ROLLBACK -> rollback(deployment, version, current);
            case STOP -> stop(deployment, version, current);
        }
        // ponytail: check-then-insert. Two concurrent queues can both pass; the later completion fails the deployment claim.
        if (executions.existsByDeployment_IdAndStatusIn(deployment.getId(), OPEN)) {
            throw new ConflictException(STATE);
        }
        return queue(deployment, version, task);
    }

    @Transactional
    public ExecutionResponse finish(Execution execution, WorkerNode worker, boolean succeeded, String message) {
        if (execution.getWorkerNode() == null || worker.getId() == null
                || !worker.getId().equals(execution.getWorkerNode().getId())) {
            throw new NotFoundException();
        }
        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            return response(execution);
        }
        Instant now = clock.instant();
        ExecutionStatus target = succeeded ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        int won = executions.finishIfRunning(execution.getId(), worker.getId(), target, now);
        if (won != 1) {
            return response(executions.findById(execution.getId()).orElseThrow(NotFoundException::new));
        }
        execution.setStatus(target);
        execution.setFinishedAt(now);
        if (succeeded) {
            try {
                apply(execution);
            } catch (ConflictException ex) {
                execution.setStatus(ExecutionStatus.FAILED);
                executions.save(execution);
                String detail = "Deployment state changed before the worker finished.";
                writeLog(execution, LogLevel.ERROR, detail);
                event(execution.getDeployment(), execution.getDeploymentVersion(), "FAILED", detail + LOCAL);
                return response(execution);
            }
        }
        String detail = sanitize(message);
        writeLog(execution, succeeded ? LogLevel.INFO : LogLevel.ERROR, detail);
        event(
                execution.getDeployment(),
                execution.getDeploymentVersion(),
                succeeded ? "SUCCEEDED" : "FAILED",
                detail + LOCAL);
        return response(execution);
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

    private void apply(Execution execution) {
        Deployment deployment = execution.getDeployment();
        DeploymentVersion version = execution.getDeploymentVersion();
        Integer current = deployment.getCurrentVersionNumber();
        DeploymentStatus next = switch (execution.getTaskType()) {
            case DEPLOY -> deploy(deployment, version, current);
            case ROLLBACK -> rollback(deployment, version, current);
            case STOP -> stop(deployment, version, current);
        };
        Integer nextVersion = execution.getTaskType() == TaskType.STOP ? current : version.getVersionNumber();
        claim(deployment, deployment.getStatus(), current, next, nextVersion);
    }

    private ExecutionResponse queue(Deployment deployment, DeploymentVersion version, TaskType task) {
        Execution execution = new Execution();
        execution.setDeployment(deployment);
        execution.setDeploymentVersion(version);
        execution.setTaskType(task);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttemptCount(0);
        execution.assignIdentity();
        execution = executions.save(execution);
        event(deployment, version, "QUEUED", "Queued " + task + " for a worker." + LOCAL);
        return response(execution);
    }

    private void writeLog(Execution execution, LogLevel level, String message) {
        ExecutionLog row = new ExecutionLog();
        row.setExecution(execution);
        row.setSequenceNumber(Math.max(1, execution.getAttemptCount() * 2));
        row.setLevel(level);
        row.setMessage(message);
        logs.save(row);
    }

    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Worker reported no detail.";
        }
        String cleaned = message.replaceAll("\\p{Cntrl}", " ").trim().replaceAll(" +", " ");
        if (cleaned.contains("Exception") && cleaned.contains(" at ")) {
            return "Worker reported a failure without a public detail.";
        }
        if (cleaned.length() > 1000) {
            return cleaned.substring(0, 1000);
        }
        return cleaned;
    }

    private static ExecutionResponse response(Execution execution) {
        return new ExecutionResponse(
                execution.getPublicId(),
                execution.getDeployment().getPublicId(),
                execution.getDeploymentVersion().getPublicId(),
                execution.getWorkerNode() == null ? null : execution.getWorkerNode().getPublicId(),
                execution.getTaskType(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt());
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
