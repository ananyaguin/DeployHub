package com.cloudflow.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.entity.Deployment;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.HealthStatus;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.WorkerStatus;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.service.DeploymentService;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentLifecycleTest extends SecurityTestBase {

    private final Map<UUID, Deployment> stored = new ConcurrentHashMap<>();
    private final Map<UUID, DeploymentVersion> versionStored = new ConcurrentHashMap<>();
    private final List<DeploymentVersion> history = new ArrayList<>();
    private final List<Execution> executionRows = new ArrayList<>();

    private Tenant acme;
    private User member;

    @Autowired
    DeploymentService lifecycle;

    @BeforeEach
    void stageDeployments() {
        stored.clear();
        versionStored.clear();
        history.clear();
        executionRows.clear();
        acme = tenant(1L, "acme");
        member = user(acme, "member@example.com", UserRole.MEMBER, true);
        when(tenants.findById(1L)).thenReturn(Optional.of(acme));
        when(deployments.save(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            if (deployment.getId() == null) {
                ReflectionTestUtils.setField(deployment, "id", 10L);
            }
            stored.put(deployment.getPublicId(), deployment);
            return deployment;
        });
        when(deployments.findByPublicIdAndTenant_Id(any(), any())).thenAnswer(this::findDeployment);
        when(deployments.findByTenant_IdAndSlug(any(), any())).thenAnswer(invocation -> stored.values().stream()
                .filter(deployment -> invocation.getArgument(0).equals(deployment.getTenant().getId()))
                .filter(deployment -> invocation.getArgument(1).equals(deployment.getSlug()))
                .findFirst());
        when(deployments.findByTenant_Id(any(), any())).thenAnswer(invocation -> new PageImpl<>(stored.values().stream()
                .filter(deployment -> invocation.getArgument(0).equals(deployment.getTenant().getId()))
                .toList()));
        when(deployments.claimFromNew(any(), any(), any(), any())).thenAnswer(invocation -> claim(invocation, true));
        when(deployments.claimFromVersion(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> claim(invocation, false));
        when(deployments.findByIdForUpdate(any())).thenAnswer(invocation -> stored.values().stream()
                .filter(deployment -> invocation.getArgument(0).equals(deployment.getId()))
                .findFirst());
        when(versions.save(any(DeploymentVersion.class))).thenAnswer(invocation -> {
            DeploymentVersion version = invocation.getArgument(0);
            if (version.getId() == null && version.getVersionNumber() != null) {
                ReflectionTestUtils.setField(version, "id", version.getVersionNumber().longValue());
            }
            history.add(version);
            versionStored.put(version.getPublicId(), version);
            return version;
        });
        when(versions.findMaxVersionNumber(any())).thenAnswer(invocation -> history.stream()
                .map(DeploymentVersion::getVersionNumber)
                .max(Integer::compareTo)
                .orElse(null));
        when(versions.findByPublicIdAndDeployment_Tenant_Id(any(), any())).thenAnswer(invocation -> {
            DeploymentVersion version = versionStored.get(invocation.getArgument(0));
            if (version == null || !invocation.getArgument(1).equals(version.getDeployment().getTenant().getId())) {
                return Optional.empty();
            }
            return Optional.of(version);
        });
        when(versions.findByDeployment_Tenant_IdAndDeployment_PublicId(any(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.copyOf(history)));
        Answer<Execution> keepOpenExecution = invocation -> {
            Execution execution = invocation.getArgument(0);
            synchronized (executionRows) {
                boolean open = execution.getStatus() == ExecutionStatus.QUEUED
                        || execution.getStatus() == ExecutionStatus.RUNNING;
                boolean duplicate = open && executionRows.stream().anyMatch(row ->
                        execution.getDeployment().getId().equals(row.getDeployment().getId())
                                && (row.getStatus() == ExecutionStatus.QUEUED
                                        || row.getStatus() == ExecutionStatus.RUNNING));
                if (duplicate) {
                    throw new DataIntegrityViolationException("uq_executions_one_open_deployment");
                }
                executionRows.add(execution);
                return execution;
            }
        };
        when(executions.save(any(Execution.class))).thenAnswer(keepOpenExecution);
        when(executions.saveAndFlush(any(Execution.class))).thenAnswer(keepOpenExecution);
        when(executions.existsByDeployment_IdAndStatusIn(any(), any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Collection<?> statuses = invocation.getArgument(1);
            synchronized (executionRows) {
                return executionRows.stream()
                        .anyMatch(row -> id.equals(row.getDeployment().getId()) && statuses.contains(row.getStatus()));
            }
        });
        when(events.save(any(DeploymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void memberCreatesListsRenamesAndKeepsVersionHistory() throws Exception {
        String created = asUser(member, post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"slug\":\"api\",\"tenantId\":\"550e8400-e29b-41d4-a716-446655440000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("api"))
                .andExpect(jsonPath("$.currentVersion").value(nullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(created.contains("550e8400-e29b-41d4-a716-446655440000"));
        Deployment deployment = stored.values().iterator().next();
        assertEquals(1L, deployment.getTenant().getId());

        asUser(member, post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"slug\":\"api\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource already exists"));

        String id = deployment.getPublicId().toString();
        asUser(member, post("/api/v1/deployments/" + id + "/versions")).andExpect(status().isCreated());
        asUser(member, post("/api/v1/deployments/" + id + "/versions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));
        asUser(member, get("/api/v1/deployments/" + id + "/versions?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].versionNumber").value(1))
                .andExpect(jsonPath("$.items[1].versionNumber").value(2));
        asUser(member, get("/api/v1/deployments?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        asUser(member, patch("/api/v1/deployments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
        asUser(member, patch("/api/v1/deployments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Deployment state cannot change that way"));
    }

    @Test
    void versionImageIsStoredWithoutEnvironmentValues() throws Exception {
        Deployment deployment = draft();
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:1.27\",\"environment\":{\"APP_MODE\":\"super-secret\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image").value("nginx:1.27"))
                .andExpect(jsonPath("$.containerId").value(nullValue()))
                .andExpect(content().string(not(containsString("super-secret"))));
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"Nginx:latest\"}"))
                .andExpect(status().isBadRequest());
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:1.27\",\"environment\":{\"PATH\":\"/tmp/super-secret\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("super-secret"))));
    }

    @Test
    void queueLeavesTheDeploymentUnchanged() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion first = version(deployment);
        DeploymentVersion second = version(deployment);
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, second, TaskType.DEPLOY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType").value("DEPLOY"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.workerId").value(nullValue()))
                .andExpect(jsonPath("$.startedAt").value(nullValue()));
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
        assertNull(deployment.getCurrentVersionNumber());
        assertEquals(TaskType.DEPLOY, executionRows.get(0).getTaskType());
        verify(events, atLeastOnce())
                .save(argThat(event -> event.getMessage().contains("No cloud provider was contacted")));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, second, TaskType.DEPLOY)))
                .andExpect(status().isConflict());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.ROLLBACK)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Deployment state cannot change that way"));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"" + deployment.getPublicId()
                                + "\",\"versionId\":\"" + first.getPublicId() + "\",\"taskType\":\"SHIP\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
    }

    @Test
    void draftCannotStopOrRollback() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion version = version(deployment);
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, version, TaskType.STOP)))
                .andExpect(status().isConflict());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, version, TaskType.ROLLBACK)))
                .andExpect(status().isConflict());
        verify(executions, never()).save(any());
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
    }

    @Test
    void otherTenantDeploymentLooksMissing() throws Exception {
        Tenant other = tenant(2L, "other");
        User stranger = user(other, "stranger@example.com", UserRole.OWNER, true);
        Deployment deployment = new Deployment();
        deployment.setTenant(other);
        deployment.setName("API");
        deployment.setSlug("api");
        deployment.setStatus(DeploymentStatus.ACTIVE);
        deployment.setCurrentVersionNumber(1);
        deployment.assignIdentity();
        ReflectionTestUtils.setField(deployment, "id", 20L);
        stored.put(deployment.getPublicId(), deployment);
        DeploymentVersion version = new DeploymentVersion();
        version.setDeployment(deployment);
        version.setVersionNumber(1);
        version.assignIdentity();
        versionStored.put(version.getPublicId(), version);

        asUser(member, get("/api/v1/deployments/" + deployment.getPublicId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, version, TaskType.DEPLOY)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, version, TaskType.ROLLBACK)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(member, get("/api/v1/deployments/" + deployment.getPublicId() + "/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(stranger, get("/api/v1/deployments/" + deployment.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("api"));
        verify(executions, never()).save(any());
    }

    @Test
    void rollbackRequiresAPreviousSuccessfulVersion() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion first = version(deployment);
        version(deployment);
        deployment.setStatus(DeploymentStatus.ACTIVE);
        deployment.setCurrentVersionNumber(2);
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.ROLLBACK)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That version has no successful deployment"));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"" + deployment.getPublicId()
                                + "\",\"versionId\":\"550e8400-e29b-41d4-a716-446655440000\",\"taskType\":\"ROLLBACK\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        verify(executions, never()).saveAndFlush(any());
        assertEquals(2, deployment.getCurrentVersionNumber());

        when(executions.existsByDeployment_IdAndDeploymentVersion_IdAndStatusAndTaskTypeIn(
                        eq(deployment.getId()), eq(first.getId()), eq(ExecutionStatus.SUCCEEDED), any()))
                .thenReturn(true);
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.ROLLBACK)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType").value("ROLLBACK"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.ROLLBACK)))
                .andExpect(status().isConflict());
        verify(executions, times(1)).saveAndFlush(any());
        assertEquals(2, deployment.getCurrentVersionNumber());
    }

    @Test
    void healthStaysUnknownWhenNoCheckExistsAndIgnoresWorkerAvailability() throws Exception {
        Deployment deployment = draft();
        mockMvc.perform(get("/api/v1/deployments/" + deployment.getPublicId() + "/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        asUser(member, get("/api/v1/deployments/" + UUID.randomUUID() + "/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(member, get("/api/v1/deployments/" + deployment.getPublicId() + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.checkedAt").value(nullValue()))
                .andExpect(jsonPath("$.detail").value(nullValue()))
                .andExpect(jsonPath("$.failureStreak").value(0))
                .andExpect(jsonPath("$.workerAvailable").value(false))
                .andExpect(jsonPath("$.simulated").value(true));
        when(workers.existsByStatusAndLastSeenAtAfter(eq(WorkerStatus.ONLINE), any())).thenReturn(true);
        asUser(member, get("/api/v1/deployments/" + deployment.getPublicId() + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.workerAvailable").value(true))
                .andExpect(jsonPath("$.simulated").value(true));
        assertEquals(HealthStatus.UNKNOWN, deployment.getHealthStatus());
    }

    @Test
    void concurrentQueuesLeaveOneOpenExecution() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion version = version(deployment);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        AtomicInteger checks = new AtomicInteger();
        when(executions.existsByDeployment_IdAndStatusIn(any(), any())).thenAnswer(invocation -> {
            if (checks.incrementAndGet() <= 2) {
                bothChecked.await(5, TimeUnit.SECONDS);
            }
            Long id = invocation.getArgument(0);
            Collection<?> statuses = invocation.getArgument(1);
            synchronized (executionRows) {
                return executionRows.stream()
                        .anyMatch(row -> id.equals(row.getDeployment().getId()) && statuses.contains(row.getStatus()));
            }
        });
        CreateExecutionRequest request = new CreateExecutionRequest(
                deployment.getPublicId().toString(), version.getPublicId().toString(), TaskType.DEPLOY);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> attempt(start, request));
        Future<Boolean> second = pool.submit(() -> attempt(start, request));
        start.countDown();
        int queued = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
        pool.shutdown();
        assertEquals(1, queued);
        assertEquals(1, executionRows.size());
        assertEquals(DeploymentStatus.DRAFT, deployment.getStatus());
        assertTrue(executionRows.stream().allMatch(row -> row.getStatus() == ExecutionStatus.QUEUED));
    }

    private boolean attempt(CountDownLatch start, CreateExecutionRequest request) throws InterruptedException {
        start.await();
        AuthenticatedUser principal = AuthenticatedUser.from(member);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            lifecycle.execute(request);
            return true;
        } catch (ConflictException ex) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Deployment draft() throws Exception {
        asUser(member, post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"slug\":\"api\"}"))
                .andExpect(status().isCreated());
        return stored.values().iterator().next();
    }

    private DeploymentVersion version(Deployment deployment) throws Exception {
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions"))
                .andExpect(status().isCreated());
        return history.get(history.size() - 1);
    }

    private static String execution(Deployment deployment, DeploymentVersion version, TaskType task) {
        return """
                {"deploymentId":"%s","versionId":"%s","taskType":"%s"}
                """
                .formatted(deployment.getPublicId(), version.getPublicId(), task);
    }

    private Optional<Deployment> findDeployment(InvocationOnMock invocation) {
        Deployment deployment = stored.get(invocation.getArgument(0));
        if (deployment == null || !invocation.getArgument(1).equals(deployment.getTenant().getId())) {
            return Optional.empty();
        }
        return Optional.of(deployment);
    }

    private int claim(InvocationOnMock invocation, boolean fromNew) {
        Long id = invocation.getArgument(0);
        DeploymentStatus expected = invocation.getArgument(1);
        synchronized (stored) {
            Deployment deployment = stored.values().stream()
                    .filter(candidate -> id.equals(candidate.getId()))
                    .findFirst()
                    .orElse(null);
            if (deployment == null || deployment.getStatus() != expected) {
                return 0;
            }
            if (fromNew) {
                if (deployment.getCurrentVersionNumber() != null) {
                    return 0;
                }
                deployment.setStatus(invocation.getArgument(2));
                deployment.setCurrentVersionNumber(invocation.getArgument(3));
                return 1;
            }
            if (!invocation.getArgument(2).equals(deployment.getCurrentVersionNumber())) {
                return 0;
            }
            deployment.setStatus(invocation.getArgument(3));
            deployment.setCurrentVersionNumber(invocation.getArgument(4));
            return 1;
        }
    }
}
