package com.cloudflow.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.cloudflow.entity.Deployment;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionLog;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.entity.WorkerNode;
import com.cloudflow.entity.WorkerStatus;
import com.cloudflow.service.WorkerService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Repository doubles emulate the conditional SQL. This class does not open MySQL.
 */
class WorkerProcessingTest extends SecurityTestBase {

    private static final String WORKER = "Worker test-worker-token";

    private final Map<String, WorkerNode> nodes = new ConcurrentHashMap<>();
    private final List<Execution> rows = new ArrayList<>();
    private final List<ExecutionLog> logRows = new ArrayList<>();
    private final AtomicLong executionIds = new AtomicLong();
    private final AtomicLong workerIds = new AtomicLong(100);

    private Tenant acme;
    private User member;

    @Autowired
    WorkerService workerService;

    @BeforeEach
    void stageWorkers() {
        nodes.clear();
        rows.clear();
        logRows.clear();
        versionById.clear();
        versionCount = 0;
        stored = null;
        acme = tenant(1L, "acme");
        member = user(acme, "member@example.com", UserRole.MEMBER, true);
        when(tenants.findById(1L)).thenReturn(Optional.of(acme));
        when(deployments.save(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            if (deployment.getId() == null) {
                ReflectionTestUtils.setField(deployment, "id", 10L);
            }
            stored = deployment;
            return deployment;
        });
        when(deployments.findByPublicIdAndTenant_Id(any(), any())).thenAnswer(invocation -> {
            if (stored == null || !invocation.getArgument(0).equals(stored.getPublicId())
                    || !invocation.getArgument(1).equals(stored.getTenant().getId())) {
                return Optional.empty();
            }
            return Optional.of(stored);
        });
        when(deployments.findByTenant_IdAndSlug(any(), any())).thenReturn(Optional.empty());
        when(deployments.claimFromNew(any(), any(), any(), any())).thenAnswer(invocation -> claimDeployment(invocation.getArguments(), true));
        when(deployments.claimFromVersion(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> claimDeployment(invocation.getArguments(), false));
        when(versions.save(any(DeploymentVersion.class))).thenAnswer(invocation -> {
            DeploymentVersion version = invocation.getArgument(0);
            versionCount = version.getVersionNumber();
            versionById.put(version.getPublicId(), version);
            return version;
        });
        when(versions.findMaxVersionNumber(any())).thenAnswer(invocation -> versionCount == 0 ? null : versionCount);
        when(versions.findByPublicIdAndDeployment_Tenant_Id(any(), any())).thenAnswer(invocation -> {
            DeploymentVersion version = versionById.get(invocation.getArgument(0));
            if (version == null || !invocation.getArgument(1).equals(acme.getId())) {
                return Optional.empty();
            }
            return Optional.of(version);
        });
        when(executions.save(any(Execution.class))).thenAnswer(invocation -> {
            Execution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                ReflectionTestUtils.setField(execution, "id", executionIds.incrementAndGet());
                rows.add(execution);
            }
            return execution;
        });
        when(executions.existsByDeployment_IdAndStatusIn(any(), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Collection<?> statuses = invocation.getArgument(1);
            synchronized (rows) {
                return rows.stream().anyMatch(row -> id.equals(row.getDeployment().getId())
                        && statuses.contains(row.getStatus()));
            }
        });
        when(executions.findFirstByStatusOrderByCreatedAtAsc(any())).thenAnswer(invocation -> {
            ExecutionStatus status = invocation.getArgument(0);
            synchronized (rows) {
                return rows.stream().filter(row -> row.getStatus() == status).findFirst();
            }
        });
        when(executions.findByStatus(any())).thenAnswer(invocation -> {
            ExecutionStatus status = invocation.getArgument(0);
            synchronized (rows) {
                return rows.stream().filter(row -> row.getStatus() == status).toList();
            }
        });
        when(executions.findByPublicId(any())).thenAnswer(invocation -> rows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getPublicId()))
                .findFirst());
        when(executions.findById(any())).thenAnswer(invocation -> rows.stream()
                .filter(row -> invocation.getArgument(0).equals(row.getId()))
                .findFirst());
        when(executions.claimQueued(any(), any(), any())).thenAnswer(invocation -> claimQueued(invocation.getArguments()));
        when(executions.finishIfRunning(any(), any(), any(), any())).thenAnswer(invocation -> finish(invocation.getArguments()));
        when(executions.requeueIfRunning(any(), anyInt()))
                .thenAnswer(invocation -> requeue(invocation.getArgument(0), invocation.getArgument(1)));
        when(executions.failIfRunning(any(), anyInt(), any()))
                .thenAnswer(invocation -> failRunning(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(workers.save(any(WorkerNode.class))).thenAnswer(invocation -> {
            WorkerNode node = invocation.getArgument(0);
            if (node.getId() == null) {
                ReflectionTestUtils.setField(node, "id", workerIds.incrementAndGet());
            }
            nodes.put(node.getName(), node);
            return node;
        });
        when(workers.findByName(any())).thenAnswer(invocation -> Optional.ofNullable(nodes.get(invocation.getArgument(0))));
        when(workers.touch(any(), any())).thenAnswer(invocation -> {
            WorkerNode node = nodeById(invocation.getArgument(0));
            if (node == null) {
                return 0;
            }
            node.setStatus(WorkerStatus.ONLINE);
            node.setLastSeenAt(invocation.getArgument(1));
            return 1;
        });
        when(workers.markStale(any())).thenAnswer(invocation -> {
            Instant cutoff = invocation.getArgument(0);
            int count = 0;
            for (WorkerNode node : nodes.values()) {
                if (node.getStatus() == WorkerStatus.ONLINE
                        && (node.getLastSeenAt() == null || node.getLastSeenAt().isBefore(cutoff))) {
                    node.setStatus(WorkerStatus.OFFLINE);
                    count++;
                }
            }
            return count;
        });
        when(events.save(any(DeploymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logs.save(any(ExecutionLog.class))).thenAnswer(invocation -> {
            logRows.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    private Deployment stored;
    private int versionCount;
    private final Map<UUID, DeploymentVersion> versionById = new ConcurrentHashMap<>();

    @Test
    void authenticationHeartbeatAndStaleWorkers() throws Exception {
        asUser(member, post("/api/v1/worker/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/worker/nodes")
                        .header(AUTHORIZATION, "Worker wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/deployments/550e8400-e29b-41d4-a716-446655440000").header(AUTHORIZATION, WORKER))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/worker/nodes")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad name\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/worker/nodes")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("worker-1"))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(content().string(not(containsString("test-worker-token"))));
        WorkerNode first = nodes.get("worker-1");
        first.setLastSeenAt(Instant.EPOCH);
        mockMvc.perform(post("/api/v1/worker/nodes")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/worker/heartbeat")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));
        assertEquals(WorkerStatus.OFFLINE, first.getStatus());
        mockMvc.perform(post("/api/v1/worker/heartbeat")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"missing-worker\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneWorkerClaimsEachQueuedExecution() throws Exception {
        Deployment deployment = draft("api");
        DeploymentVersion version = version(deployment);
        queue(deployment, version, TaskType.DEPLOY);
        register("worker-1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> claimOnce(start));
        Future<Boolean> second = pool.submit(() -> claimOnce(start));
        start.countDown();
        assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        pool.shutdown();
        assertEquals(ExecutionStatus.RUNNING, rows.get(0).getStatus());
        assertEquals(1, rows.get(0).getAttemptCount());
        mockMvc.perform(post("/api/v1/worker/tasks/claim")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void workerCompletesDeployRollbackAndStop() throws Exception {
        Deployment deployment = draft("api");
        DeploymentVersion first = version(deployment);
        DeploymentVersion second = version(deployment);
        String queued = queue(deployment, second, TaskType.DEPLOY);
        register("worker-1");
        mockMvc.perform(post("/api/v1/worker/tasks/claim")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("DEPLOY"))
                .andExpect(jsonPath("$.attempt").value(1));
        finish(queued, true, "Simulated DEPLOY completed. No cloud provider was contacted.");
        assertEquals(DeploymentStatus.ACTIVE, deployment.getStatus());
        assertEquals(2, deployment.getCurrentVersionNumber());
        assertTrue(logRows.stream().noneMatch(row -> row.getMessage().contains("test-worker-token")));

        String rollback = queue(deployment, first, TaskType.ROLLBACK);
        claim();
        finish(rollback, true, "Simulated ROLLBACK completed.");
        assertEquals(1, deployment.getCurrentVersionNumber());

        String stop = queue(deployment, first, TaskType.STOP);
        claim();
        finish(stop, true, "Simulated STOP completed.");
        assertEquals(DeploymentStatus.ARCHIVED, deployment.getStatus());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionBody(deployment, first, TaskType.DEPLOY)))
                .andExpect(status().isConflict());
        verify(deployments, times(1)).claimFromNew(any(), any(), any(), any());
        verify(deployments, times(2)).claimFromVersion(any(), any(), any(), any(), any());
    }

    @Test
    void failedResultAndForeignWorkerDoNotChangeTheDeployment() throws Exception {
        Deployment deployment = draft("api");
        DeploymentVersion version = version(deployment);
        String queued = queue(deployment, version, TaskType.DEPLOY);
        register("worker-1");
        register("worker-2");
        claim();
        mockMvc.perform(post("/api/v1/worker/tasks/" + queued + "/result")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-2\",\"succeeded\":true,\"message\":\"nope\"}"))
                .andExpect(status().isNotFound());
        assertEquals(ExecutionStatus.RUNNING, rows.get(0).getStatus());
        finish(queued, false, "IllegalStateException at com.cloudflow.Secret");
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
        assertEquals(ExecutionStatus.FAILED, rows.get(0).getStatus());
        assertTrue(logRows.stream().noneMatch(row -> row.getMessage().contains("Secret")));
        assertEquals(0, rows.stream().filter(row -> row.getStatus() == ExecutionStatus.QUEUED).count());
    }

    @Test
    void interruptedWorkRetriesThenStops() throws Exception {
        register("worker-1");
        WorkerNode worker = nodes.get("worker-1");
        Deployment deployment = new Deployment();
        deployment.setTenant(acme);
        deployment.setName("API");
        deployment.setSlug("api");
        deployment.setStatus(DeploymentStatus.DRAFT);
        deployment.assignIdentity();
        ReflectionTestUtils.setField(deployment, "id", 10L);
        stored = deployment;
        DeploymentVersion version = new DeploymentVersion();
        version.setDeployment(deployment);
        version.setVersionNumber(1);
        version.assignIdentity();
        Execution execution = running(deployment, version, worker, 2, Instant.now());
        worker.setStatus(WorkerStatus.OFFLINE);
        workerService.sweep();
        assertEquals(ExecutionStatus.QUEUED, execution.getStatus());
        assertNull(execution.getWorkerNode());

        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setWorkerNode(worker);
        execution.setAttemptCount(3);
        execution.setStartedAt(Instant.now());
        workerService.sweep();
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());

        Execution hung = running(deployment, version, worker, 1, Instant.EPOCH);
        worker.setStatus(WorkerStatus.ONLINE);
        worker.setLastSeenAt(Instant.now());
        workerService.sweep();
        assertEquals(ExecutionStatus.QUEUED, hung.getStatus());
    }

    @Test
    void draftStopIsRejected() throws Exception {
        Deployment deployment = draft("api");
        DeploymentVersion version = version(deployment);
        int before = rows.size();
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionBody(deployment, version, TaskType.STOP)))
                .andExpect(status().isConflict());
        assertEquals(before, rows.size());
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
    }

    private boolean claimOnce(CountDownLatch start) throws InterruptedException {
        start.await();
        return workerService.claim("worker-1").isPresent();
    }

    private void register(String name) throws Exception {
        mockMvc.perform(post("/api/v1/worker/nodes")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk());
    }

    private void claim() throws Exception {
        mockMvc.perform(post("/api/v1/worker/tasks/claim")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\"}"))
                .andExpect(status().isOk());
    }

    private void finish(String id, boolean succeeded, String message) throws Exception {
        mockMvc.perform(post("/api/v1/worker/tasks/" + id + "/result")
                        .header(AUTHORIZATION, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"worker-1\",\"succeeded\":" + succeeded + ",\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Secret"))));
    }

    private Deployment draft(String slug) throws Exception {
        asUser(member, post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated());
        return stored;
    }

    private DeploymentVersion version(Deployment deployment) throws Exception {
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions"))
                .andExpect(status().isCreated());
        return versionById.values().stream()
                .filter(version -> version.getDeployment() == deployment)
                .max(Comparator.comparing(DeploymentVersion::getVersionNumber))
                .orElseThrow();
    }

    private String queue(Deployment deployment, DeploymentVersion version, TaskType task) throws Exception {
        String body = asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionBody(deployment, version, task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.publicId");
    }

    private static String executionBody(Deployment deployment, DeploymentVersion version, TaskType task) {
        return """
                {"deploymentId":"%s","versionId":"%s","taskType":"%s"}
                """
                .formatted(deployment.getPublicId(), version.getPublicId(), task);
    }

    private Execution running(Deployment deployment, DeploymentVersion version, WorkerNode worker, int attempt, Instant started) {
        Execution execution = new Execution();
        execution.setDeployment(deployment);
        execution.setDeploymentVersion(version);
        execution.setWorkerNode(worker);
        execution.setTaskType(TaskType.DEPLOY);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setAttemptCount(attempt);
        execution.setStartedAt(started);
        execution.assignIdentity();
        ReflectionTestUtils.setField(execution, "id", executionIds.incrementAndGet());
        rows.add(execution);
        return execution;
    }

    private int claimQueued(Object[] arguments) {
        Long id = (Long) arguments[0];
        WorkerNode worker = (WorkerNode) arguments[1];
        Instant started = (Instant) arguments[2];
        synchronized (rows) {
            Execution execution = rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
            if (execution == null || execution.getStatus() != ExecutionStatus.QUEUED) {
                return 0;
            }
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setWorkerNode(worker);
            execution.setStartedAt(started);
            execution.setAttemptCount(execution.getAttemptCount() + 1);
            return 1;
        }
    }

    private int finish(Object[] arguments) {
        Long id = (Long) arguments[0];
        Long workerId = (Long) arguments[1];
        ExecutionStatus status = (ExecutionStatus) arguments[2];
        Instant finished = (Instant) arguments[3];
        synchronized (rows) {
            Execution execution = rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
            if (execution == null || execution.getStatus() != ExecutionStatus.RUNNING
                    || execution.getWorkerNode() == null || !workerId.equals(execution.getWorkerNode().getId())) {
                return 0;
            }
            execution.setStatus(status);
            execution.setFinishedAt(finished);
            return 1;
        }
    }

    private int requeue(Long id, int maxAttempts) {
        synchronized (rows) {
            Execution execution = rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
            if (execution == null || execution.getStatus() != ExecutionStatus.RUNNING
                    || execution.getAttemptCount() >= maxAttempts) {
                return 0;
            }
            execution.setStatus(ExecutionStatus.QUEUED);
            execution.setWorkerNode(null);
            execution.setStartedAt(null);
            return 1;
        }
    }

    private int failRunning(Long id, int maxAttempts, Instant finished) {
        synchronized (rows) {
            Execution execution = rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
            if (execution == null || execution.getStatus() != ExecutionStatus.RUNNING
                    || execution.getAttemptCount() < maxAttempts) {
                return 0;
            }
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFinishedAt(finished);
            return 1;
        }
    }

    private int claimDeployment(Object[] arguments, boolean fromNew) {
        Long id = (Long) arguments[0];
        DeploymentStatus expected = (DeploymentStatus) arguments[1];
        synchronized (rows) {
            if (stored == null || !id.equals(stored.getId()) || stored.getStatus() != expected) {
                return 0;
            }
            if (fromNew) {
                if (stored.getCurrentVersionNumber() != null) {
                    return 0;
                }
                stored.setStatus((DeploymentStatus) arguments[2]);
                stored.setCurrentVersionNumber((Integer) arguments[3]);
                return 1;
            }
            if (!arguments[2].equals(stored.getCurrentVersionNumber())) {
                return 0;
            }
            stored.setStatus((DeploymentStatus) arguments[3]);
            stored.setCurrentVersionNumber((Integer) arguments[4]);
            return 1;
        }
    }

    private WorkerNode nodeById(Long id) {
        return nodes.values().stream().filter(node -> id.equals(node.getId())).findFirst().orElse(null);
    }
}
