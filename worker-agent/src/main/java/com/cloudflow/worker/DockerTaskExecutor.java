package com.cloudflow.worker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Talks to the local Docker daemon. It does not call Kubernetes or a cloud API.
 * A failure here stays a failed task. It does not switch to {@link SimulatedTaskExecutor}.
 */
public final class DockerTaskExecutor implements DeploymentExecutor {

    static final String MANAGED = "com.cloudflow.managed";
    static final String TENANT = "com.cloudflow.tenant";
    static final String DEPLOYMENT = "com.cloudflow.deployment";
    static final String VERSION = "com.cloudflow.version";

    private static final Set<String> FORBIDDEN_ENV = Set.of(
            "PATH", "HOME", "IFS", "ENV", "BASH_ENV", "DOCKER_HOST",
            "LD_PRELOAD", "LD_LIBRARY_PATH", "LD_AUDIT", "LD_DEBUG");

    private final DockerEngine engine;
    private final long memoryBytes;
    private final long nanoCpus;

    public DockerTaskExecutor(DockerEngine engine, long memoryBytes, long nanoCpus) {
        this.engine = engine;
        this.memoryBytes = memoryBytes;
        this.nanoCpus = nanoCpus;
    }

    @Override
    public TaskResult execute(ClaimedTask task) {
        try {
            engine.ping();
            return switch (task.taskType()) {
                case "DEPLOY", "ROLLBACK" -> replace(task);
                case "STOP" -> stop(task);
                default -> TaskResult.failed("Unsupported task.");
            };
        } catch (DockerAccessException ex) {
            return TaskResult.failed("Docker is unavailable.");
        } catch (RuntimeException ex) {
            return TaskResult.failed("Docker could not complete the task.");
        }
    }

    static String containerName(UUID tenantId, UUID deploymentId, int version) {
        return "cf-" + tenantId + "-" + deploymentId + "-v" + version;
    }

    private TaskResult replace(ClaimedTask task) {
        if (!task.configurationValid()) {
            return TaskResult.failed("Deployment configuration could not be read.");
        }
        if (!ImageRef.valid(task.image())) {
            return TaskResult.failed("Image reference is not allowed.");
        }
        if (!environmentAllowed(task.environment())) {
            return TaskResult.failed("Environment is not allowed.");
        }
        String id = ensureRunning(task);
        if (task.currentVersion() != null && task.currentVersion() != task.versionNumber()) {
            try {
                stopOwned(task.tenantId(), task.deploymentId(), task.currentVersion());
            } catch (RuntimeException ex) {
                stopQuietly(id);
                throw ex;
            }
        }
        return TaskResult.ok("Docker " + task.taskType() + " completed.", id);
    }

    private TaskResult stop(ClaimedTask task) {
        stopOwned(task.tenantId(), task.deploymentId(), task.versionNumber());
        return TaskResult.ok("Docker STOP completed.", null);
    }

    private String ensureRunning(ClaimedTask task) {
        ContainerView existing = one(engine.find(task.tenantId(), task.deploymentId(), task.versionNumber()), task);
        if (existing != null) {
            if (!existing.running()) {
                engine.start(existing.id());
            }
            return existing.id();
        }
        ContainerView created = engine.create(new ContainerSpec(
                containerName(task.tenantId(), task.deploymentId(), task.versionNumber()),
                task.image(),
                task.environment(),
                labels(task.tenantId(), task.deploymentId(), task.versionNumber()),
                memoryBytes,
                nanoCpus,
                task.tenantId(),
                task.deploymentId(),
                task.versionNumber()));
        return created.id();
    }

    private void stopOwned(UUID tenantId, UUID deploymentId, int version) {
        ContainerView existing = one(engine.find(tenantId, deploymentId, version), tenantId, deploymentId, version);
        if (existing == null || !existing.running()) {
            return;
        }
        engine.stop(existing.id());
    }

    private void stopQuietly(String containerId) {
        try {
            engine.stop(containerId);
        } catch (RuntimeException ex) {
            // The task is already failing. Leave the safe message to the caller.
        }
    }

    private ContainerView one(List<ContainerView> found, ClaimedTask task) {
        return one(found, task.tenantId(), task.deploymentId(), task.versionNumber());
    }

    private ContainerView one(List<ContainerView> found, UUID tenantId, UUID deploymentId, int version) {
        if (found == null || found.isEmpty()) {
            return null;
        }
        if (found.size() != 1) {
            throw new IllegalStateException("ambiguous");
        }
        ContainerView container = found.get(0);
        if (!tenantId.equals(container.tenantId())
                || !deploymentId.equals(container.deploymentId())
                || container.version() != version
                || unsafe(container)) {
            throw new IllegalStateException("rejected");
        }
        return container;
    }

    private boolean unsafe(ContainerView container) {
        return container.privileged()
                || container.hostNetwork()
                || container.bindCount() > 0
                || container.memoryBytes() > memoryBytes
                || container.nanoCpus() > nanoCpus;
    }

    private static boolean environmentAllowed(Map<String, String> environment) {
        if (environment == null) {
            return true;
        }
        if (environment.size() > 20) {
            return false;
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !key.matches("^[A-Z][A-Z0-9_]{0,62}$") || FORBIDDEN_ENV.contains(key)) {
                return false;
            }
            if (value == null || value.length() > 256 || value.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
                return false;
            }
        }
        return true;
    }

    static Map<String, String> labels(UUID tenantId, UUID deploymentId, int version) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(MANAGED, "true");
        labels.put(TENANT, tenantId.toString());
        labels.put(DEPLOYMENT, deploymentId.toString());
        labels.put(VERSION, Integer.toString(version));
        return labels;
    }
}
