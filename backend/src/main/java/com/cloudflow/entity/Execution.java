package com.cloudflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "executions")
public class Execution extends TimestampedPublicEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_id", nullable = false, updatable = false)
    private Deployment deployment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_version_id", nullable = false, updatable = false)
    private DeploymentVersion deploymentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_node_id")
    private WorkerNode workerNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, updatable = false, length = 32)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public DeploymentVersion getDeploymentVersion() {
        return deploymentVersion;
    }

    public void setDeploymentVersion(DeploymentVersion deploymentVersion) {
        this.deploymentVersion = deploymentVersion;
    }

    public WorkerNode getWorkerNode() {
        return workerNode;
    }

    public void setWorkerNode(WorkerNode workerNode) {
        this.workerNode = workerNode;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}
