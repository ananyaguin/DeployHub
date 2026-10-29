package com.cloudflow.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cloudflow.entity.DeliveryStatus;
import com.cloudflow.entity.Deployment;
import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.DeploymentVersion;
import com.cloudflow.entity.Execution;
import com.cloudflow.entity.ExecutionStatus;
import com.cloudflow.entity.NotificationChannel;
import com.cloudflow.entity.NotificationDelivery;
import com.cloudflow.entity.NotificationPreference;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.entity.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationTest extends SecurityTestBase {

    @Test
    void executionHistoryRejectsAnonymousAndMissingIds() throws Exception {
        mockMvc.perform(get("/api/v1/deployments/" + UUID.randomUUID() + "/executions?page=0&size=20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, true);
        asUser(owner, get("/api/v1/executions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
        asUser(owner, get("/api/v1/deployments/" + UUID.randomUUID() + "/executions?page=0&size=20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void memberCannotCreateUsers() throws Exception {
        User member = user(tenant(1L, "acme"), "member@example.com", UserRole.MEMBER, true);
        asUser(member, post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Operation not permitted"));
        verify(users, never()).save(any());
    }

    @Test
    void adminCannotGrantAdmin() throws Exception {
        User admin = user(tenant(1L, "acme"), "admin@example.com", UserRole.ADMIN, true);
        asUser(admin, post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void addedUserJoinsTheCallerTenant() throws Exception {
        Tenant acme = tenant(1L, "acme");
        User owner = user(acme, "owner@example.com", UserRole.OWNER, true);
        when(tenants.findById(1L)).thenReturn(Optional.of(acme));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        asUser(owner, post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"new@example.com","password":"%s","displayName":"New","role":"MEMBER","tenantId":"550e8400-e29b-41d4-a716-446655440000"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(1L, saved.getValue().getTenant().getId());
        assertEquals(acme.getPublicId(), saved.getValue().getTenant().getPublicId());
    }

    @Test
    void otherTenantResourcesLookMissing() throws Exception {
        Tenant acme = tenant(1L, "acme");
        Tenant other = tenant(2L, "other");
        User owner = user(acme, "owner@example.com", UserRole.OWNER, true);
        User stranger = user(other, "stranger@example.com", UserRole.OWNER, true);

        Deployment deployment = deployment(other);
        DeploymentVersion version = version(deployment);
        Execution execution = execution(deployment, version);
        when(deployments.findByPublicIdAndTenant_Id(deployment.getPublicId(), 2L))
                .thenReturn(Optional.of(deployment));
        when(versions.findByPublicIdAndDeployment_Tenant_Id(version.getPublicId(), 2L))
                .thenReturn(Optional.of(version));
        when(executions.findByPublicIdAndDeployment_Tenant_Id(execution.getPublicId(), 2L))
                .thenReturn(Optional.of(execution));

        NotificationPreference preference = new NotificationPreference();
        preference.setEmailEnabled(false);
        when(preferences.findByUser_PublicIdAndUser_Tenant_Id(stranger.getPublicId(), 2L))
                .thenReturn(Optional.of(preference));
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setStatus(DeliveryStatus.SENT);
        when(deliveries.findByUser_Tenant_IdAndUser_PublicId(eq(2L), eq(stranger.getPublicId()), any()))
                .thenReturn(new PageImpl<>(List.of(delivery)));

        expectMissing(owner, get("/api/v1/deployments/" + deployment.getPublicId()));
        expectFound(stranger, get("/api/v1/deployments/" + deployment.getPublicId()), "$.slug", "api");

        expectMissing(
                owner,
                get("/api/v1/deployments/" + deployment.getPublicId() + "/versions/" + version.getPublicId()));
        expectFound(
                stranger,
                get("/api/v1/deployments/" + deployment.getPublicId() + "/versions/" + version.getPublicId()),
                "$.versionNumber",
                3);

        expectMissing(owner, get("/api/v1/deployments/" + deployment.getPublicId() + "/events?page=0&size=20"));
        expectFound(
                stranger, get("/api/v1/deployments/" + deployment.getPublicId() + "/events?page=0&size=20"), "$.total", 0);

        execution.setAttemptCount(2);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setStartedAt(Instant.parse("2026-01-02T03:04:05Z"));
        execution.setFinishedAt(Instant.parse("2026-01-02T03:05:05Z"));
        when(executions.findByDeployment_PublicIdAndDeployment_Tenant_Id(
                        eq(deployment.getPublicId()), eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of(execution)));

        expectMissing(owner, get("/api/v1/executions/" + execution.getPublicId()));
        expectFound(stranger, get("/api/v1/executions/" + execution.getPublicId()), "$.taskType", "DEPLOY");
        expectFound(stranger, get("/api/v1/executions/" + execution.getPublicId()), "$.attemptCount", 2);

        String history = "/api/v1/deployments/" + deployment.getPublicId() + "/executions?page=0&size=20";
        expectMissing(owner, get(history));
        expectFound(stranger, get(history), "$.total", 1);
        expectFound(stranger, get(history), "$.items[0].status", "FAILED");
        expectFound(stranger, get(history), "$.items[0].attemptCount", 2);

        expectMissing(owner, get("/api/v1/executions/" + execution.getPublicId() + "/logs?page=0&size=20"));
        expectFound(stranger, get("/api/v1/executions/" + execution.getPublicId() + "/logs?page=0&size=20"), "$.total", 0);

        expectMissing(owner, get("/api/v1/notification-preference"));
        expectFound(stranger, get("/api/v1/notification-preference"), "$.emailEnabled", false);

        expectFound(owner, get("/api/v1/notification-deliveries?page=0&size=20"), "$.total", 0);
        expectFound(stranger, get("/api/v1/notification-deliveries?page=0&size=20"), "$.total", 1);

        verify(deployments, atLeastOnce()).findByPublicIdAndTenant_Id(deployment.getPublicId(), 1L);
    }

    @Test
    void operationsCountsStayInsideTheTenantAndActuatorStaysClosed() throws Exception {
        mockMvc.perform(get("/api/v1/operations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/operations").header("Authorization", "Worker test-worker-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        when(executions.countByDeployment_Tenant_IdAndStatus(eq(1L), eq(ExecutionStatus.QUEUED))).thenReturn(2L);
        when(executions.countByDeployment_Tenant_IdAndStatus(eq(1L), eq(ExecutionStatus.RUNNING))).thenReturn(1L);
        when(executions.countByDeployment_Tenant_IdAndStatus(eq(1L), eq(ExecutionStatus.SUCCEEDED))).thenReturn(4L);
        when(executions.countByDeployment_Tenant_IdAndStatus(eq(1L), eq(ExecutionStatus.FAILED))).thenReturn(3L);
        when(executions.countByDeployment_Tenant_IdAndAttemptCountGreaterThan(eq(1L), eq(1))).thenReturn(2L);
        when(executions.countByDeployment_Tenant_IdAndTaskTypeAndStatus(
                eq(1L), eq(TaskType.ROLLBACK), eq(ExecutionStatus.SUCCEEDED))).thenReturn(1L);
        when(executions.countByDeployment_Tenant_IdAndTaskTypeAndStatus(
                eq(1L), eq(TaskType.ROLLBACK), eq(ExecutionStatus.FAILED))).thenReturn(1L);
        when(executions.queuedDemand(1L)).thenReturn(List.<Object[]>of(new Object[] {5000, 64}, new Object[] {100, 64}));
        when(workers.countByStatus(WorkerStatus.ONLINE)).thenReturn(1L);
        when(workers.countByStatus(WorkerStatus.OFFLINE)).thenReturn(2L);
        when(workers.onlineCapacity()).thenReturn(List.<Object[]>of(new Object[] {1000, 512}));

        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, true);
        User stranger = user(tenant(2L, "other"), "bob@example.com", UserRole.OWNER, true);
        expectFound(owner, get("/api/v1/operations"), "$.queued", 2);
        expectFound(owner, get("/api/v1/operations"), "$.running", 1);
        expectFound(owner, get("/api/v1/operations"), "$.succeeded", 4);
        expectFound(owner, get("/api/v1/operations"), "$.failed", 3);
        expectFound(owner, get("/api/v1/operations"), "$.retried", 2);
        expectFound(owner, get("/api/v1/operations"), "$.rollbackSucceeded", 1);
        expectFound(owner, get("/api/v1/operations"), "$.rollbackFailed", 1);
        expectFound(owner, get("/api/v1/operations"), "$.schedulingBlocked", 1);
        expectFound(owner, get("/api/v1/operations"), "$.workersOnline", 1);
        expectFound(owner, get("/api/v1/operations"), "$.workersOffline", 2);
        expectFound(owner, get("/api/v1/operations"), "$.capacityDeclared", true);
        expectFound(stranger, get("/api/v1/operations"), "$.queued", 0);
        expectFound(stranger, get("/api/v1/operations"), "$.schedulingBlocked", 0);

        when(workers.onlineCapacity()).thenReturn(List.of());
        when(executions.queuedDemand(1L)).thenReturn(List.<Object[]>of(new Object[] {100, 64}));
        expectFound(owner, get("/api/v1/operations"), "$.schedulingBlocked", 1);
    }

    private void expectMissing(User user, MockHttpServletRequestBuilder request)
            throws Exception {
        asUser(user, request).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Resource not found"));
    }

    private void expectFound(
            User user,
            MockHttpServletRequestBuilder request,
            String path,
            Object value)
            throws Exception {
        ResultActions actions = asUser(user, request).andExpect(status().isOk());
        if (value instanceof Integer number) {
            actions.andExpect(jsonPath(path).value(number));
        } else if (value instanceof Boolean flag) {
            actions.andExpect(jsonPath(path).value(flag));
        } else {
            actions.andExpect(jsonPath(path).value(String.valueOf(value)));
        }
    }

    private static Deployment deployment(Tenant tenant) {
        Deployment deployment = new Deployment();
        deployment.setTenant(tenant);
        deployment.setName("API");
        deployment.setSlug("api");
        deployment.setStatus(DeploymentStatus.ACTIVE);
        deployment.assignIdentity();
        return deployment;
    }

    private static DeploymentVersion version(Deployment deployment) {
        DeploymentVersion version = new DeploymentVersion();
        version.setDeployment(deployment);
        version.setVersionNumber(3);
        version.assignIdentity();
        return version;
    }

    private static Execution execution(Deployment deployment, DeploymentVersion version) {
        Execution execution = new Execution();
        execution.setDeployment(deployment);
        execution.setDeploymentVersion(version);
        execution.setTaskType(TaskType.DEPLOY);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.assignIdentity();
        return execution;
    }

    private static String userJson(String role) {
        return """
                {"email":"new@example.com","password":"%s","displayName":"New","role":"%s"}
                """
                .formatted(PASSWORD, role);
    }
}
