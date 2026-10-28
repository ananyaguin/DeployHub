package com.cloudflow.worker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedTaskExecutorTest {

    @Test
    void standardHandlersReportSimulatedSuccess() {
        SimulatedTaskExecutor executor = SimulatedTaskExecutor.standard();
        executor.executeType("DEPLOY");
        executor.executeType("ROLLBACK");
        executor.executeType("STOP");
        executor.executeType("HEALTH");
        assertTrue(executor.successDetail("DEPLOY").contains("No cloud provider was contacted."));
        RecordingApi api = new RecordingApi(ClaimedTask.simple(UUID.randomUUID(), "DEPLOY", 1));
        WorkerLoop loop = loop(api, executor);
        loop.pollOnce();
        assertEquals(Boolean.TRUE, api.succeeded.get());
        assertTrue(api.message.get().contains("Simulated DEPLOY"));
        assertFalse(api.message.get().contains("secret-token"));
    }

    @Test
    void healthCheckIsSimulatedAndFailsWithoutAnActiveVersion() {
        SimulatedTaskExecutor executor = SimulatedTaskExecutor.standard();
        TaskResult missing = executor.execute(ClaimedTask.simple(UUID.randomUUID(), "HEALTH", 1));
        assertFalse(missing.succeeded());
        assertTrue(missing.message().contains("No active version is deployed."));
        assertTrue(missing.message().contains("No cloud provider was contacted."));
        TaskResult healthy = executor.execute(new ClaimedTask(
                UUID.randomUUID(), "HEALTH", 1, UUID.randomUUID(), UUID.randomUUID(), 2, null, Map.of(), 2, null, true));
        assertTrue(healthy.succeeded());
        assertTrue(healthy.message().contains("Simulated HEALTH"));
        assertTrue(healthy.message().contains("No cloud provider was contacted."));
        assertFalse(healthy.message().contains("secret-token"));
    }

    @Test
    void handlerFailureDoesNotReportSuccessOrTheExceptionText() {
        SimulatedTaskExecutor executor = new SimulatedTaskExecutor(List.of(new TaskHandler() {
            @Override
            public String taskType() {
                return "DEPLOY";
            }

            @Override
            public void execute() {
                throw new IllegalStateException("secret-token-boom");
            }
        }));
        RecordingApi api = new RecordingApi(ClaimedTask.simple(UUID.randomUUID(), "DEPLOY", 1));
        WorkerLoop loop = loop(api, executor);
        loop.pollOnce();
        assertEquals(Boolean.FALSE, api.succeeded.get());
        assertFalse(api.message.get().contains("secret-token-boom"));
        assertFalse(api.message.get().contains("Unsupported"));
        assertTrue(api.message.get().contains("No cloud provider was contacted."));
    }

    private static WorkerLoop loop(RecordingApi api, SimulatedTaskExecutor executor) {
        return new WorkerLoop(
                new WorkerAgentProperties(
                        "http://localhost:8080", "secret-token", "worker-1", Duration.ofSeconds(2),
                        "simulator", "", 256, 500),
                api,
                executor);
    }

    private static final class RecordingApi implements WorkerApi {
        private final ClaimedTask task;
        private final AtomicReference<Boolean> succeeded = new AtomicReference<>();
        private final AtomicReference<String> message = new AtomicReference<>();

        private RecordingApi(ClaimedTask task) {
            this.task = task;
        }

        @Override
        public void register(String name) {}

        @Override
        public void heartbeat(String name) {}

        @Override
        public Optional<ClaimedTask> claim(String name) {
            return Optional.of(task);
        }

        @Override
        public ReportOutcome complete(String name, UUID executionId, boolean succeeded, String message, String containerId) {
            this.succeeded.set(succeeded);
            this.message.set(message);
            return ReportOutcome.ACCEPTED;
        }
    }
}
