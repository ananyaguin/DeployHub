package com.cloudflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cloudflow.dto.request.CreateDeploymentRequest;
import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.dto.request.CreateVersionRequest;
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
import com.cloudflow.entity.DeliveryStatus;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.HealthStatus;
import com.cloudflow.entity.LogLevel;
import com.cloudflow.entity.NotificationChannel;
import com.cloudflow.entity.NotificationDelivery;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.User;
import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.Tenant;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.exception.TokenRejectedException;
import com.cloudflow.exception.UnauthorizedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.ExecutionLogRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.NotificationDeliveryRepository;
import com.cloudflow.repository.TenantRepository;
import com.cloudflow.repository.UserRepository;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.security.CurrentUserAccessor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final int DEFAULT_CPU = 100;
    private static final int DEFAULT_MEMORY = 64;
    private static final Set<String> FORBIDDEN_ENV = Set.of(
            "PATH", "HOME", "IFS", "ENV", "BASH_ENV", "DOCKER_HOST",
            "LD_PRELOAD", "LD_LIBRARY_PATH", "LD_AUDIT", "LD_DEBUG");

    private final CurrentUserAccessor currentUser;
    private final TenantRepository tenants;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentEventRepository events;
    private final ExecutionRepository executions;
    private final ExecutionLogRepository logs;
    private final NotificationDeliveryRepository deliveries;
    private final UserRepository users;
    private final ObjectMapper json;
    private final Clock clock;

    public DeploymentService(
            CurrentUserAccessor currentUser,
            TenantRepository tenants,
            DeploymentRepository deployments,
            DeploymentVersionRepository versions,
            DeploymentEventRepository events,
            ExecutionRepository executions,
            ExecutionLogRepository logs,
            NotificationDeliveryRepository deliveries,
            UserRepository users,
            ObjectMapper json,
            Clock clock) {
        this.currentUser = currentUser;
        this.tenants = tenants;
        this.deployments = deployments;
        this.versions = versions;
        this.events = events;
        this.executions = executions;
        this.logs = logs;
        this.deliveries = deliveries;
        this.users = users;
        this.json = json;
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
    public DeploymentVersionResponse addVersion(UUID publicId, CreateVersionRequest request) {
        Deployment deployment = requireDeployment(publicId);
        if (deployment.getStatus() == DeploymentStatus.ARCHIVED) {
            throw new ConflictException(STATE);
        }
        Integer max = versions.findMaxVersionNumber(deployment.getId());
        DeploymentVersion version = new DeploymentVersion();
        version.setDeployment(deployment);
        version.setVersionNumber(max == null ? 1 : max + 1);
        if (request != null) {
            checkEnvironment(request.environment());
            version.setImageRef(request.image());
            version.setEnvJson(writeEnvironment(request.environment()));
        }
        version.assignIdentity();
        version = versions.save(version);
        event(deployment, version, "VERSION_CREATED", "Version " + version.getVersionNumber() + " recorded.");
        return versionResponse(version);
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
                        .map(DeploymentService::versionResponse)
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
        int cpu = request.cpuMillis() == null ? DEFAULT_CPU : request.cpuMillis();
        int memory = request.memoryMb() == null ? DEFAULT_MEMORY : request.memoryMb();
        return switch (request.taskType()) {
            case DEPLOY -> change(deployment, version, TaskType.DEPLOY, cpu, memory);
            case ROLLBACK -> change(deployment, version, TaskType.ROLLBACK, cpu, memory);
            case STOP -> change(deployment, version, TaskType.STOP, cpu, memory);
            case HEALTH -> change(deployment, version, TaskType.HEALTH, cpu, memory);
        };
    }

    private ExecutionResponse change(
            Deployment deployment, DeploymentVersion version, TaskType task, int cpu, int memory) {
        Integer current = deployment.getCurrentVersionNumber();
        switch (task) {
            case DEPLOY -> deploy(deployment, version, current);
            case ROLLBACK -> rollback(deployment, version, current);
            case STOP -> stop(deployment, version, current);
            case HEALTH -> healthCheck(deployment, version, current);
        }
        deployments.findByIdForUpdate(deployment.getId()).orElseThrow(NotFoundException::new);
        if (executions.existsByDeployment_IdAndStatusIn(deployment.getId(), OPEN)) {
            throw new ConflictException(STATE);
        }
        return queue(deployment, version, task, cpu, memory);
    }

    @Transactional
    public ExecutionResponse finish(
            Execution execution, WorkerNode worker, boolean succeeded, String message, String containerId) {
        if (execution.getWorkerNode() == null || worker.getId() == null
                || !worker.getId().equals(execution.getWorkerNode().getId())) {
            throw new NotFoundException();
        }
        ExecutionResponse replay = sameResult(execution, succeeded);
        if (replay != null) {
            return replay;
        }
        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            throw new ConflictException(STATE);
        }
        Instant now = clock.instant();
        ExecutionStatus target = succeeded ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        int won = executions.finishIfRunning(execution.getId(), worker.getId(), target, now);
        execution = executions.findById(execution.getId()).orElseThrow(NotFoundException::new);
        if (won != 1) {
            ExecutionResponse raced = sameResult(execution, succeeded);
            if (raced != null) {
                return raced;
            }
            throw new ConflictException(STATE);
        }
        execution.setStatus(target);
        execution.setFinishedAt(now);
        if (succeeded && execution.getTaskType() != TaskType.HEALTH) {
            try {
                apply(execution);
            } catch (ConflictException ex) {
                execution = executions.findById(execution.getId()).orElseThrow(NotFoundException::new);
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setFinishedAt(now);
                String detail = "Deployment state changed before the worker finished.";
                writeLog(execution, LogLevel.ERROR, detail);
                event(execution.getDeployment(), execution.getDeploymentVersion(), "FAILED", noted(detail));
                return response(execution);
            }
        }
        execution = executions.findById(execution.getId()).orElseThrow(NotFoundException::new);
        if (succeeded && containerId != null && execution.getTaskType() != TaskType.HEALTH) {
            execution.getDeploymentVersion().setContainerId(containerId);
            versions.save(execution.getDeploymentVersion());
        }
        String detail = sanitize(message);
        String actor = "worker:" + worker.getName();
        if (execution.getTaskType() == TaskType.HEALTH) {
            recordSimulatedHealth(execution, succeeded, detail, actor);
        }
        writeLog(execution, succeeded ? LogLevel.INFO : LogLevel.ERROR, detail);
        event(
                execution.getDeployment(),
                execution.getDeploymentVersion(),
                execution,
                succeeded ? "SUCCEEDED" : "FAILED",
                noted(detail),
                actor);
        if (!succeeded && execution.getTaskType() != TaskType.HEALTH) {
            notice(execution, execution.getTaskType() + " failed. The previous version stays in place.");
        }
        if (succeeded && execution.getTaskType() == TaskType.ROLLBACK) {
            notice(execution, "Rollback succeeded. No cloud provider was contacted.");
        }
        return response(execution);
    }

    private static DeploymentStatus deploy(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() == DeploymentStatus.ARCHIVED
                || (current != null && version.getVersionNumber() <= current)) {
            throw new ConflictException(STATE);
        }
        return DeploymentStatus.ACTIVE;
    }

    private DeploymentStatus rollback(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() != DeploymentStatus.ACTIVE
                || current == null
                || version.getVersionNumber() >= current) {
            throw new ConflictException(STATE);
        }
        if (!previouslySucceeded(deployment, version)) {
            throw new ConflictException("That version has no successful deployment");
        }
        return DeploymentStatus.ACTIVE;
    }

    private static void healthCheck(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() != DeploymentStatus.ACTIVE
                || current == null
                || !current.equals(version.getVersionNumber())) {
            throw new ConflictException(STATE);
        }
    }

    private static DeploymentStatus stop(Deployment deployment, DeploymentVersion version, Integer current) {
        if (deployment.getStatus() != DeploymentStatus.ACTIVE
                || current == null
                || !current.equals(version.getVersionNumber())) {
            throw new ConflictException(STATE);
        }
        return DeploymentStatus.ARCHIVED;
    }

    private boolean previouslySucceeded(Deployment deployment, DeploymentVersion version) {
        if (deployment.getId() == null || version.getId() == null) {
            return false;
        }
        return executions.existsByDeployment_IdAndDeploymentVersion_IdAndStatusAndTaskTypeIn(
                deployment.getId(),
                version.getId(),
                ExecutionStatus.SUCCEEDED,
                EnumSet.of(TaskType.DEPLOY, TaskType.ROLLBACK));
    }

    private void recordSimulatedHealth(Execution execution, boolean succeeded, String detail, String actor) {
        String storedDetail = detail.length() > 500 ? detail.substring(0, 500) : detail;
        Deployment deployment = execution.getDeployment();
        deployment.setHealthCheckedAt(clock.instant());
        deployment.setHealthDetail(storedDetail);
        if (succeeded) {
            deployment.setHealthStatus(HealthStatus.HEALTHY);
            deployment.setHealthFailureStreak(0);
        } else {
            int streak = deployment.getHealthFailureStreak() + 1;
            deployment.setHealthFailureStreak(streak);
            deployment.setHealthStatus(HealthStatus.UNHEALTHY);
            if (streak == 1) {
                notice(execution, "Simulated health is unhealthy. No cloud provider was contacted.");
            }
            if (streak == 3) {
                recoverOnce(deployment, actor);
            }
        }
        deployments.save(deployment);
    }

    private void recoverOnce(Deployment deployment, String actor) {
        Integer current = deployment.getCurrentVersionNumber();
        if (deployment.getStatus() != DeploymentStatus.ACTIVE || current == null) {
            return;
        }
        List<DeploymentVersion> earlier = versions
                .findByDeployment_IdAndVersionNumberLessThanOrderByVersionNumberDesc(deployment.getId(), current);
        if (earlier == null) {
            return;
        }
        for (DeploymentVersion candidate : earlier) {
            if (!previouslySucceeded(deployment, candidate)) {
                continue;
            }
            try {
                queue(deployment, candidate, TaskType.ROLLBACK, DEFAULT_CPU, DEFAULT_MEMORY);
            } catch (ConflictException ex) {
                event(
                        deployment,
                        candidate,
                        null,
                        "UNHEALTHY",
                        "Simulated health failed 3 times. Rollback was not queued." + LOCAL,
                        actor);
                return;
            }
            event(
                    deployment,
                    candidate,
                    null,
                    "UNHEALTHY",
                    "Simulated health failed 3 times. Queued rollback to version "
                            + candidate.getVersionNumber()
                            + "."
                            + LOCAL,
                    actor);
            return;
        }
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
            case HEALTH -> throw new ConflictException(STATE);
        };
        Integer nextVersion = execution.getTaskType() == TaskType.STOP ? current : version.getVersionNumber();
        claim(deployment, deployment.getStatus(), current, next, nextVersion);
    }

    private ExecutionResponse queue(
            Deployment deployment, DeploymentVersion version, TaskType task, int cpu, int memory) {
        Execution execution = new Execution();
        execution.setDeployment(deployment);
        execution.setDeploymentVersion(version);
        execution.setTaskType(task);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttemptCount(0);
        execution.setCpuMillis(cpu);
        execution.setMemoryMb(memory);
        execution.assignIdentity();
        try {
            execution = executions.saveAndFlush(execution);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(STATE);
        }
        event(deployment, version, execution, "QUEUED", "Queued " + task + " for a worker." + LOCAL, null);
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

    private static ExecutionResponse sameResult(Execution execution, boolean succeeded) {
        if (execution.getStatus() != ExecutionStatus.SUCCEEDED && execution.getStatus() != ExecutionStatus.FAILED) {
            return null;
        }
        boolean recordedSuccess = execution.getStatus() == ExecutionStatus.SUCCEEDED;
        if (recordedSuccess != succeeded) {
            throw new ConflictException(STATE);
        }
        return response(execution);
    }

    private static String noted(String detail) {
        return detail.contains("No cloud provider was contacted") ? detail : detail + LOCAL;
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

    public Map<String, String> environmentOf(DeploymentVersion version) {
        if (version.getEnvJson() == null || version.getEnvJson().isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(version.getEnvJson(), new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void checkEnvironment(Map<String, String> environment) {
        if (environment == null || environment.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !key.matches("^[A-Z][A-Z0-9_]{0,62}$") || FORBIDDEN_ENV.contains(key)) {
                throw new TokenRejectedException();
            }
            if (value == null || value.length() > 256 || value.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
                throw new TokenRejectedException();
            }
        }
    }

    private String writeEnvironment(Map<String, String> environment) {
        if (environment == null || environment.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(environment);
        } catch (JsonProcessingException ex) {
            throw new TokenRejectedException();
        }
    }

    private static DeploymentVersionResponse versionResponse(DeploymentVersion version) {
        return new DeploymentVersionResponse(
                version.getPublicId(), version.getVersionNumber(), version.getImageRef(), version.getContainerId());
    }

    private static ExecutionResponse response(Execution execution) {
        return new ExecutionResponse(
                execution.getPublicId(),
                execution.getDeployment().getPublicId(),
                execution.getDeploymentVersion().getPublicId(),
                execution.getWorkerNode() == null ? null : execution.getWorkerNode().getPublicId(),
                execution.getTaskType(),
                execution.getStatus(),
                execution.getAttemptCount(),
                execution.getStartedAt(),
                execution.getFinishedAt());
    }

    private void event(Deployment deployment, DeploymentVersion version, String type, String message) {
        event(deployment, version, null, type, message, null);
    }

    private void event(
            Deployment deployment,
            DeploymentVersion version,
            Execution execution,
            String type,
            String message,
            String actor) {
        DeploymentEvent event = new DeploymentEvent();
        event.setDeployment(deployment);
        event.setDeploymentVersion(version);
        event.setExecution(execution);
        event.setEventType(type);
        event.setMessage(message);
        if (actor == null) {
            actor = currentUser.current().map(AuthenticatedUser::getUsername).orElse(null);
        }
        event.setActorLabel(actor);
        events.save(event);
    }

    private void notice(Execution execution, String summary) {
        if (execution.getId() == null || execution.getDeployment().getTenant() == null) {
            return;
        }
        String text = summary.length() > 500 ? summary.substring(0, 500) : summary;
        for (User user : users.findByTenant_Id(execution.getDeployment().getTenant().getId(), PageRequest.of(0, 50))) {
            if (user.getId() == null
                    || deliveries.existsByUser_IdAndExecution_IdAndChannel(
                            user.getId(), execution.getId(), NotificationChannel.IN_APP)) {
                continue;
            }
            NotificationDelivery delivery = new NotificationDelivery();
            delivery.setUser(user);
            delivery.setExecution(execution);
            delivery.setChannel(NotificationChannel.IN_APP);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setSentAt(clock.instant());
            delivery.setSummary(text);
            try {
                deliveries.save(delivery);
            } catch (DataIntegrityViolationException ex) {
                // The unique key already recorded this notice.
            }
        }
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
