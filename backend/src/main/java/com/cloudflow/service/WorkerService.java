package com.cloudflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.cloudflow.configuration.WorkerProperties;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.WorkerNodeResponse;
import com.cloudflow.dto.response.WorkerTaskResponse;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionLog;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.LogLevel;
import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.WorkerStatus;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.ExecutionLogRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.worker.WorkerNodeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {

    private static final String STATE = "Deployment state cannot change that way";
    private static final String LOCAL = " No cloud provider was contacted.";

    private final WorkerProperties properties;
    private final WorkerNodeRepository workers;
    private final ExecutionRepository executions;
    private final ExecutionLogRepository logs;
    private final DeploymentEventRepository events;
    private final DeploymentService deployments;
    private final Clock clock;

    public WorkerService(
            WorkerProperties properties,
            WorkerNodeRepository workers,
            ExecutionRepository executions,
            ExecutionLogRepository logs,
            DeploymentEventRepository events,
            DeploymentService deployments,
            Clock clock) {
        this.properties = properties;
        this.workers = workers;
        this.executions = executions;
        this.logs = logs;
        this.events = events;
        this.deployments = deployments;
        this.clock = clock;
    }

    @Transactional
    public WorkerNodeResponse register(String name) {
        sweep();
        Instant now = clock.instant();
        WorkerNode node = workers.findByName(name).orElse(null);
        if (node == null) {
            node = new WorkerNode();
            node.setName(name);
            node.setStatus(WorkerStatus.ONLINE);
            node.setLastSeenAt(now);
            node.assignIdentity();
            try {
                node = workers.save(node);
            } catch (DataIntegrityViolationException ex) {
                node = workers.findByName(name).orElseThrow(NotFoundException::new);
            }
        }
        workers.touch(node.getId(), now);
        node.setStatus(WorkerStatus.ONLINE);
        node.setLastSeenAt(now);
        return response(node);
    }

    @Transactional
    public WorkerNodeResponse heartbeat(String name) {
        sweep();
        WorkerNode node = require(name);
        Instant now = clock.instant();
        workers.touch(node.getId(), now);
        node.setStatus(WorkerStatus.ONLINE);
        node.setLastSeenAt(now);
        return response(node);
    }

    @Transactional
    public Optional<WorkerTaskResponse> claim(String name) {
        sweep();
        WorkerNode worker = require(name);
        if (worker.getStatus() != WorkerStatus.ONLINE) {
            throw new ConflictException(STATE);
        }
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 5; attempt++) {
            Execution next = executions
                    .findFirstByStatusOrderByCreatedAtAsc(ExecutionStatus.QUEUED)
                    .orElse(null);
            if (next == null) {
                return Optional.empty();
            }
            int won = executions.claimQueued(next.getId(), worker, now);
            if (won != 1) {
                continue;
            }
            Execution claimed = executions.findById(next.getId()).orElseThrow(NotFoundException::new);
            writeLog(claimed, claimed.getAttemptCount() * 2 - 1, LogLevel.INFO, "Claimed by " + worker.getName() + ".");
            DeploymentVersion version = claimed.getDeploymentVersion();
            Map<String, String> environment = deployments.environmentOf(version);
            return Optional.of(new WorkerTaskResponse(
                    claimed.getPublicId(),
                    claimed.getTaskType(),
                    claimed.getAttemptCount(),
                    claimed.getDeployment().getTenant().getPublicId(),
                    claimed.getDeployment().getPublicId(),
                    version.getVersionNumber(),
                    version.getImageRef(),
                    environment == null ? Map.of() : environment,
                    claimed.getDeployment().getCurrentVersionNumber(),
                    version.getContainerId(),
                    environment != null));
        }
        return Optional.empty();
    }

    @Transactional
    public ExecutionResponse finish(
            UUID publicId, String name, boolean succeeded, String message, String containerId) {
        WorkerNode worker = require(name);
        Execution execution = executions.findByPublicId(publicId).orElseThrow(NotFoundException::new);
        return deployments.finish(execution, worker, succeeded, message, containerId);
    }

    @Scheduled(
            fixedDelayString = "${cloudflow.worker.sweep-interval:30s}",
            initialDelayString = "${cloudflow.worker.sweep-initial-delay:30s}")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        workers.markStale(now.minus(properties.heartbeatTimeout()));
        List<Execution> running = executions.findByStatus(ExecutionStatus.RUNNING);
        if (running == null) {
            return;
        }
        for (Execution execution : running) {
            if (interrupted(execution, now)) {
                recover(execution, now);
            }
        }
    }

    private boolean interrupted(Execution execution, Instant now) {
        Instant started = execution.getStartedAt();
        if (started != null && started.isBefore(now.minus(properties.runningTimeout()))) {
            return true;
        }
        WorkerNode worker = execution.getWorkerNode();
        if (worker == null || worker.getStatus() != WorkerStatus.ONLINE) {
            return true;
        }
        Instant seen = worker.getLastSeenAt();
        return seen == null || seen.isBefore(now.minus(properties.heartbeatTimeout()));
    }

    private void recover(Execution execution, Instant now) {
        if (execution.getAttemptCount() >= properties.maxAttempts()) {
            int rows = executions.failIfRunning(execution.getId(), properties.maxAttempts(), now);
            if (rows != 1) {
                return;
            }
            execution = executions.findById(execution.getId()).orElseThrow(NotFoundException::new);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFinishedAt(now);
            String detail = "Stopped after " + execution.getAttemptCount()
                    + " attempts. The last attempt's result was not recorded.";
            writeLog(execution, execution.getAttemptCount() * 2, LogLevel.ERROR, detail);
            event(execution, "INTERRUPTED", detail + LOCAL);
            return;
        }
        int rows = executions.requeueIfRunning(execution.getId(), properties.maxAttempts());
        if (rows != 1) {
            return;
        }
        execution = executions.findById(execution.getId()).orElseThrow(NotFoundException::new);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setWorkerNode(null);
        execution.setStartedAt(null);
        String detail = "Requeued after interruption. Attempt " + execution.getAttemptCount()
                + " of " + properties.maxAttempts()
                + ". The previous attempt's result was not recorded.";
        writeLog(execution, execution.getAttemptCount() * 2, LogLevel.WARN, detail);
        event(execution, "INTERRUPTED", detail + LOCAL);
    }

    private WorkerNode require(String name) {
        return workers.findByName(name).orElseThrow(NotFoundException::new);
    }

    private void writeLog(Execution execution, int sequence, LogLevel level, String message) {
        ExecutionLog row = new ExecutionLog();
        row.setExecution(execution);
        row.setSequenceNumber(Math.max(1, sequence));
        row.setLevel(level);
        row.setMessage(message);
        logs.save(row);
    }

    private void event(Execution execution, String type, String message) {
        DeploymentEvent event = new DeploymentEvent();
        event.setDeployment(execution.getDeployment());
        event.setDeploymentVersion(execution.getDeploymentVersion());
        event.setEventType(type);
        event.setMessage(message);
        events.save(event);
    }

    private static WorkerNodeResponse response(WorkerNode node) {
        return new WorkerNodeResponse(node.getPublicId(), node.getName(), node.getStatus(), node.getLastSeenAt());
    }
}
