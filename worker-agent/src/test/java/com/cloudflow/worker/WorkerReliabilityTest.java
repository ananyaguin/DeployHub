package com.cloudflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkerReliabilityTest {

    private static final String TOKEN = "worker-token-value";

    @Test
    void rejectedWorkerTokenDoesNotLeakTheToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/worker/nodes", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            WorkerHttpClient client = client(server.getAddress().getPort());
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.register("worker-1"));
            assertEquals("Worker request failed", failure.getMessage());
            assertFalse(failure.getMessage().contains(TOKEN));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectionFailureStaysBoundedAndHidesTheToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.start();
        server.stop(0);
        WorkerHttpClient client = client(port);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.heartbeat("worker-1"));
        assertEquals("Worker request failed", failure.getMessage());
        assertFalse(String.valueOf(failure.getCause()).contains(TOKEN));
    }

    @Test
    void taskFailureIsReportedWithoutTheExceptionText() {
        RecordingApi api = new RecordingApi();
        WorkerLoop loop = loop(api, task -> {
            throw new IllegalStateException("token=" + TOKEN);
        });
        loop.runTask(ClaimedTask.simple(UUID.randomUUID(), "DEPLOY", 1));
        assertEquals(1, api.calls.get());
        assertFalse(api.succeeded);
        assertEquals("Task failed.", api.message);
        assertFalse(api.message.contains(TOKEN));
    }

    @Test
    void lostReportIsRetriedWithoutRunningTheTaskAgain() {
        AtomicInteger runs = new AtomicInteger();
        RecordingApi api = new RecordingApi();
        api.failuresRemaining = 1;
        WorkerLoop loop = loop(api, task -> {
            runs.incrementAndGet();
            return TaskResult.ok("done", null);
        });
        loop.pollOnce();
        assertEquals(1, runs.get());
        assertEquals(1, api.calls.get());
        loop.pollOnce();
        assertEquals(1, runs.get());
        assertEquals(2, api.calls.get());
        assertTrue(api.succeeded);
        assertEquals("done", api.message);
    }

    @Test
    void rejectedResultIsNotRunAgain() {
        AtomicInteger runs = new AtomicInteger();
        RecordingApi api = new RecordingApi();
        api.reject = true;
        WorkerLoop loop = loop(api, task -> {
            runs.incrementAndGet();
            return TaskResult.ok("done", null);
        });
        loop.pollOnce();
        loop.pollOnce();
        assertEquals(1, runs.get());
        assertEquals(1, api.calls.get());
    }

    @Test
    void conflictAndConnectionFailureDoNotEchoTheToken() throws Exception {
        HttpServer conflict = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        conflict.createContext("/api/v1/worker/tasks/", exchange -> {
            exchange.sendResponseHeaders(409, -1);
            exchange.close();
        });
        conflict.start();
        try {
            WorkerHttpClient client = client(conflict.getAddress().getPort());
            assertEquals(
                    ReportOutcome.REJECTED,
                    client.complete(UUID.randomUUID().toString(), UUID.randomUUID(), true, "done", null));
        } finally {
            conflict.stop(0);
        }
        HttpServer down = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = down.getAddress().getPort();
        down.start();
        down.stop(0);
        assertEquals(
                ReportOutcome.RETRY,
                client(port).complete("worker-1", UUID.randomUUID(), false, "Task failed.", null));
    }

    private static WorkerHttpClient client(int port) {
        return new WorkerHttpClient(properties("http://127.0.0.1:" + port));
    }

    private static WorkerLoop loop(WorkerApi api, DeploymentExecutor executor) {
        return new WorkerLoop(properties("http://127.0.0.1:9"), api, executor);
    }

    private static WorkerAgentProperties properties(String backendUrl) {
        return new WorkerAgentProperties(backendUrl, TOKEN, "worker-1", Duration.ofMillis(1), "simulator", "", 256, 500);
    }

    private static final class RecordingApi implements WorkerApi {
        private final AtomicInteger calls = new AtomicInteger();
        private final ClaimedTask task = ClaimedTask.simple(UUID.randomUUID(), "DEPLOY", 1);
        private int failuresRemaining;
        private boolean reject;
        private boolean claimed;
        private boolean succeeded;
        private String message = "";

        @Override
        public void register(String name) {}

        @Override
        public void heartbeat(String name) {}

        @Override
        public Optional<ClaimedTask> claim(String name) {
            if (claimed) {
                return Optional.empty();
            }
            claimed = true;
            return Optional.of(task);
        }

        @Override
        public ReportOutcome complete(String name, UUID executionId, boolean succeeded, String message, String containerId) {
            calls.incrementAndGet();
            if (reject) {
                return ReportOutcome.REJECTED;
            }
            if (failuresRemaining > 0) {
                failuresRemaining--;
                return ReportOutcome.RETRY;
            }
            this.succeeded = succeeded;
            this.message = message;
            return ReportOutcome.ACCEPTED;
        }
    }
}
