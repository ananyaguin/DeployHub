package com.cloudflow.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cloudflow.dto.request.CreateExecutionRequest;
import com.cloudflow.entity.Deployment;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.entity.DeploymentEvent;
import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.service.DeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(versions.save(any(DeploymentVersion.class))).thenAnswer(invocation -> {
            DeploymentVersion version = invocation.getArgument(0);
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
        when(executions.save(any(Execution.class))).thenAnswer(invocation -> {
            Execution execution = invocation.getArgument(0);
            executionRows.add(execution);
            return execution;
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
    void deployRollbackAndStopFollowTheExistingStatuses() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion first = version(deployment);
        DeploymentVersion second = version(deployment);
        String body = execution(deployment, second, TaskType.DEPLOY);
        asUser(member, post("/api/v1/executions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType").value("DEPLOY"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.workerId").value(nullValue()));
        assertEquals(DeploymentStatus.ACTIVE, deployment.getStatus());
        assertEquals(2, deployment.getCurrentVersionNumber());
        assertTrue(executionRows.get(0).getTaskType() == TaskType.DEPLOY);
        assertNull(executionRows.get(0).getWorkerNode());
        verify(events, atLeastOnce())
                .save(argThat(event -> event.getMessage().contains("No cloud provider was contacted")));

        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, second, TaskType.DEPLOY)))
                .andExpect(status().isConflict());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.ROLLBACK)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType").value("ROLLBACK"));
        assertEquals(1, deployment.getCurrentVersionNumber());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, second, TaskType.ROLLBACK)))
                .andExpect(status().isConflict());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.STOP)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskType").value("STOP"));
        assertEquals(DeploymentStatus.ARCHIVED, deployment.getStatus());
        asUser(member, post("/api/v1/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execution(deployment, first, TaskType.DEPLOY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Deployment state cannot change that way"));
        asUser(member, post("/api/v1/deployments/" + deployment.getPublicId() + "/versions"))
                .andExpect(status().isConflict());
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
                .andExpect(status().isNotFound());
        asUser(stranger, get("/api/v1/deployments/" + deployment.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("api"));
        verify(executions, never()).save(any());
    }

    @Test
    void concurrentDeploysClaimTheDeploymentOnce() throws Exception {
        Deployment deployment = draft();
        DeploymentVersion version = version(deployment);
        CreateExecutionRequest request = new CreateExecutionRequest(
                deployment.getPublicId().toString(), version.getPublicId().toString(), TaskType.DEPLOY);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> attempt(start, request));
        Future<Boolean> second = pool.submit(() -> attempt(start, request));
        start.countDown();
        assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        pool.shutdown();
        assertEquals(DeploymentStatus.ACTIVE, deployment.getStatus());
        assertEquals(1, executionRows.size());
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
