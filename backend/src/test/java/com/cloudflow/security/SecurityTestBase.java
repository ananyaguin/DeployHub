package com.cloudflow.security;

import java.time.Instant;
import java.util.Optional;

import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.EmailVerificationTokenRepository;
import com.cloudflow.repository.ExecutionLogRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.NotificationDeliveryRepository;
import com.cloudflow.repository.NotificationPreferenceRepository;
import com.cloudflow.repository.PasswordResetTokenRepository;
import com.cloudflow.repository.TenantRepository;
import com.cloudflow.repository.UserRepository;
import com.cloudflow.service.AccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@SpringBootTest
@AutoConfigureMockMvc
abstract class SecurityTestBase {

    static final String PASSWORD = "correct-horse";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    TenantRepository tenants;

    @Autowired
    DeploymentRepository deployments;

    @Autowired
    DeploymentVersionRepository versions;

    @Autowired
    DeploymentEventRepository events;

    @Autowired
    ExecutionRepository executions;

    @Autowired
    ExecutionLogRepository logs;

    @Autowired
    NotificationPreferenceRepository preferences;

    @Autowired
    NotificationDeliveryRepository deliveries;

    @Autowired
    EmailVerificationTokenRepository verifications;

    @Autowired
    PasswordResetTokenRepository resets;

    @MockitoBean
    JavaMailSender mail;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AccessTokenService tokens;

    @BeforeEach
    void emptyRepositories() {
        reset(
                users,
                tenants,
                deployments,
                versions,
                events,
                executions,
                logs,
                preferences,
                deliveries,
                verifications,
                resets,
                mail);
        when(users.findByPublicId(any())).thenReturn(Optional.empty());
        when(users.findByTenant_IdAndEmail(any(), any())).thenReturn(Optional.empty());
        when(users.findByTenant_Id(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(tenants.findBySlug(any())).thenReturn(Optional.empty());
        when(tenants.findById(any())).thenReturn(Optional.empty());
        when(deployments.findByPublicIdAndTenant_Id(any(), any())).thenReturn(Optional.empty());
        when(versions.findByPublicIdAndDeployment_Tenant_Id(any(), any())).thenReturn(Optional.empty());
        when(events.findByDeployment_Tenant_IdAndDeployment_PublicId(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(executions.findByPublicIdAndDeployment_Tenant_Id(any(), any())).thenReturn(Optional.empty());
        when(logs.findByExecution_PublicIdAndExecution_Deployment_Tenant_Id(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(preferences.findByUser_PublicIdAndUser_Tenant_Id(any(), any())).thenReturn(Optional.empty());
        when(deliveries.findByUser_Tenant_IdAndUser_PublicId(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(verifications.findByTokenHash(any())).thenReturn(Optional.empty());
        when(verifications.consume(any(), any())).thenReturn(0);
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resets.findByTokenHash(any())).thenReturn(Optional.empty());
        when(resets.consume(any(), any())).thenReturn(0);
        when(resets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    Tenant tenant(long id, String slug) {
        Tenant tenant = new Tenant();
        tenant.setName(slug);
        tenant.setSlug(slug);
        tenant.assignIdentity();
        ReflectionTestUtils.setField(tenant, "id", id);
        return tenant;
    }

    User user(Tenant tenant, String email, UserRole role, boolean verified) {
        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setDisplayName(email);
        user.setRole(role);
        user.setEmailVerifiedAt(verified ? Instant.parse("2026-01-01T00:00:00Z") : null);
        user.assignIdentity();
        when(users.findByPublicId(user.getPublicId())).thenReturn(Optional.of(user));
        return user;
    }

    ResultActions asUser(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + tokens.issue(user).accessToken()));
    }
}
