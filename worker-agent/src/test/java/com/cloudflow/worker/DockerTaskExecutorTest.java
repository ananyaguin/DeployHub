package com.cloudflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DockerTaskExecutorTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPLOYMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long MEMORY = 256L * 1024L * 1024L;
    private static final long CPU = 500_000_000L;

    @Test
    void rejectsImagesAndEnvironmentBeforeCallingDocker() {
        assertFalse(ImageRef.valid("nginx"));
        assertFalse(ImageRef.valid("nginx:1.27;touch"));
        assertFalse(ImageRef.valid("../nginx:1"));
        assertTrue(ImageRef.valid("nginx:1.27"));
        assertTrue(ImageRef.valid("library/nginx:1.27"));
        assertTrue(ImageRef.valid("nginx@sha256:" + "a".repeat(64)));
        FakeDocker docker = new FakeDocker();
        DockerTaskExecutor executor = executor(docker);
        assertFalse(executor.execute(task("DEPLOY", 1, "nginx", Map.of(), null)).succeeded());
        assertFalse(executor.execute(task("DEPLOY", 1, "nginx:1.27", Map.of("PATH", "secret-value"), null)).succeeded());
        assertEquals(0, docker.created.size());
        assertFalse(executor.execute(task("DEPLOY", 1, "nginx:1.27", Map.of("PATH", "secret-value"), null))
                .message()
                .contains("secret-value"));
        ClaimedTask unreadable = new ClaimedTask(
                UUID.randomUUID(), "DEPLOY", 1, TENANT, DEPLOYMENT, 1, "nginx:1.27", Map.of(), null, null, false);
        assertEquals("Deployment configuration could not be read.", executor.execute(unreadable).message());
    }

    @Test
    void unavailableDaemonFailsWithoutSimulation() {
        FakeDocker docker = new FakeDocker();
        docker.down = true;
        TaskResult result = executor(docker).execute(task("DEPLOY", 1, "nginx:1.27", Map.of(), null));
        assertFalse(result.succeeded());
        assertEquals("Docker is unavailable.", result.message());
        assertFalse(result.message().contains("docker.sock"));
        assertEquals(0, docker.created.size());
    }

    @Test
    void deployCreatesOneLimitedContainerAndARetryReusesIt() {
        FakeDocker docker = new FakeDocker();
        DockerTaskExecutor executor = executor(docker);
        TaskResult first = executor.execute(task("DEPLOY", 1, "nginx:1.27", Map.of("APP_MODE", "prod"), null));
        assertTrue(first.succeeded());
        assertEquals("cccccccccccccccc", first.containerId());
        ContainerSpec spec = docker.created.get(0);
        assertEquals(MEMORY, spec.memoryBytes());
        assertEquals(CPU, spec.nanoCpus());
        assertEquals("true", spec.labels().get(DockerTaskExecutor.MANAGED));
        assertEquals(TENANT.toString(), spec.labels().get(DockerTaskExecutor.TENANT));
        assertTrue(spec.name().startsWith("cf-" + TENANT));
        TaskResult second = executor.execute(task("DEPLOY", 1, "nginx:1.27", Map.of(), null));
        assertTrue(second.succeeded());
        assertEquals(first.containerId(), second.containerId());
        assertEquals(1, docker.created.size());
    }

    @Test
    void stoppedContainerIsStartedInsteadOfRecreated() {
        FakeDocker docker = new FakeDocker();
        docker.views.add(view("abc123abc123", false, TENANT, DEPLOYMENT, 1, false, MEMORY, CPU));
        TaskResult result = executor(docker).execute(task("DEPLOY", 1, "nginx:1.27", Map.of(), null));
        assertTrue(result.succeeded());
        assertEquals("abc123abc123", result.containerId());
        assertEquals(List.of("abc123abc123"), docker.started);
        assertEquals(0, docker.created.size());
    }

    @Test
    void stopIsIdempotentAndDoesNotTouchAnotherTenant() {
        FakeDocker docker = new FakeDocker();
        TaskResult missing = executor(docker).execute(task("STOP", 1, null, Map.of(), null));
        assertTrue(missing.succeeded());
        assertEquals(0, docker.stopped.size());

        docker.views.add(view("abc123abc123", false, TENANT, DEPLOYMENT, 1, false, MEMORY, CPU));
        assertTrue(executor(docker).execute(task("STOP", 1, null, Map.of(), null)).succeeded());
        assertEquals(0, docker.stopped.size());

        docker.views.clear();
        docker.views.add(view("abc123abc123", true, TENANT, DEPLOYMENT, 1, false, MEMORY, CPU));
        assertTrue(executor(docker).execute(task("STOP", 1, null, Map.of(), null)).succeeded());
        assertEquals(List.of("abc123abc123"), docker.stopped);

        docker.stopped.clear();
        docker.leak = view("ffffffffffffffff", true, UUID.randomUUID(), DEPLOYMENT, 1, false, MEMORY, CPU);
        TaskResult foreign = executor(docker).execute(task("STOP", 1, null, Map.of(), null));
        assertFalse(foreign.succeeded());
        assertEquals(0, docker.stopped.size());
    }

    @Test
    void rollbackStopsThePreviousVersionOnlyAfterTheTargetStarts() {
        FakeDocker docker = new FakeDocker();
        docker.views.add(view("bbbbbbbbbbbb", true, TENANT, DEPLOYMENT, 2, false, MEMORY, CPU));
        TaskResult result = executor(docker).execute(task("ROLLBACK", 1, "nginx:1.27", Map.of(), 2));
        assertTrue(result.succeeded());
        assertEquals(1, docker.created.size());
        assertEquals(List.of("bbbbbbbbbbbb"), docker.stopped);

        docker.failStop = true;
        docker.created.clear();
        docker.stopped.clear();
        docker.views.clear();
        docker.views.add(view("bbbbbbbbbbbb", true, TENANT, DEPLOYMENT, 2, false, MEMORY, CPU));
        TaskResult failed = executor(docker).execute(task("ROLLBACK", 1, "nginx:1.27", Map.of(), 2));
        assertFalse(failed.succeeded());
        assertFalse(failed.message().contains("docker.sock"));
        assertEquals(1, docker.created.size());
    }

    @Test
    void unsafeExistingContainerIsNotStarted() {
        FakeDocker docker = new FakeDocker();
        docker.views.add(view("abc123abc123", false, TENANT, DEPLOYMENT, 1, true, MEMORY, CPU));
        TaskResult privileged = executor(docker).execute(task("DEPLOY", 1, "nginx:1.27", Map.of(), null));
        assertFalse(privileged.succeeded());
        assertEquals(0, docker.started.size());

        docker.views.clear();
        docker.views.add(view("abc123abc123", false, TENANT, DEPLOYMENT, 1, false, MEMORY + 1, CPU));
        assertFalse(executor(docker).execute(task("DEPLOY", 1, "nginx:1.27", Map.of(), null)).succeeded());
        assertEquals(0, docker.created.size());

        docker.views.clear();
        docker.views.add(view("abc123abc123", true, TENANT, DEPLOYMENT, 1, false, MEMORY, CPU));
        docker.views.add(view("dddddddddddd", true, TENANT, DEPLOYMENT, 1, false, MEMORY, CPU));
        assertFalse(executor(docker).execute(task("STOP", 1, null, Map.of(), null)).succeeded());
        assertEquals(0, docker.stopped.size());
    }

    @Test
    void dockerModeDoesNotAcceptAMissingHost() {
        assertThrows(IllegalStateException.class, () -> new WorkerAgentProperties(
                "http://localhost:8080", "token", "worker-1", java.time.Duration.ofSeconds(2), "docker", "", 256, 500));
        assertThrows(IllegalStateException.class, () -> new WorkerAgentProperties(
                "http://localhost:8080", "token", "worker-1", java.time.Duration.ofSeconds(2), "simulator", "", 8, 500));
    }

    @Test
    void claimPayloadKeepsEnvironmentForTheWorkerOnly() throws Exception {
        String body = "{\"publicId\":\"33333333-3333-3333-3333-333333333333\",\"taskType\":\"DEPLOY\",\"attempt\":1,"
                + "\"tenantId\":\"" + TENANT + "\",\"deploymentId\":\"" + DEPLOYMENT + "\",\"versionNumber\":1,"
                + "\"image\":\"nginx:1.27\",\"environment\":{\"APP_MODE\":\"hidden-value\"},"
                + "\"currentVersion\":null,\"containerId\":null,\"configurationValid\":true}";
        ClaimedTask task = ClaimedTask.parse(new ObjectMapper(), body);
        assertEquals("hidden-value", task.environment().get("APP_MODE"));
        assertEquals("nginx:1.27", task.image());
    }

    private static DockerTaskExecutor executor(FakeDocker docker) {
        return new DockerTaskExecutor(docker, MEMORY, CPU);
    }

    private static ClaimedTask task(
            String type, int version, String image, Map<String, String> environment, Integer current) {
        return new ClaimedTask(
                UUID.randomUUID(), type, 1, TENANT, DEPLOYMENT, version, image, environment, current, null, true);
    }

    private static ContainerView view(
            String id, boolean running, UUID tenant, UUID deployment, int version, boolean privileged,
            long memory, long cpu) {
        return new ContainerView(id, running, tenant, deployment, version, memory, cpu, privileged, false, 0);
    }

    private static final class FakeDocker implements DockerEngine {
        private final List<ContainerView> views = new ArrayList<>();
        private final List<ContainerSpec> created = new ArrayList<>();
        private final List<String> started = new ArrayList<>();
        private final List<String> stopped = new ArrayList<>();
        private boolean down;
        private boolean failStop;
        private ContainerView leak;

        @Override
        public void ping() {
            if (down) {
                throw new DockerAccessException();
            }
        }

        @Override
        public List<ContainerView> find(UUID tenantId, UUID deploymentId, int version) {
            if (leak != null) {
                return List.of(leak);
            }
            return views.stream()
                    .filter(view -> tenantId.equals(view.tenantId())
                            && deploymentId.equals(view.deploymentId())
                            && view.version() == version)
                    .toList();
        }

        @Override
        public ContainerView create(ContainerSpec spec) {
            created.add(spec);
            ContainerView view = view(
                    "cccccccccccccccc", true, spec.tenantId(), spec.deploymentId(), spec.version(), false,
                    spec.memoryBytes(), spec.nanoCpus());
            views.add(view);
            return view;
        }

        @Override
        public void start(String containerId) {
            started.add(containerId);
        }

        @Override
        public void stop(String containerId) {
            if (failStop) {
                throw new IllegalStateException("refused /var/run/docker.sock");
            }
            stopped.add(containerId);
        }
    }
}
