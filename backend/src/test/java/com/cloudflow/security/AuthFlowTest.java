package com.cloudflow.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends SecurityTestBase {

    private static final String SECRET = "test-only-jwt-secret-must-be-32-bytes!!";

    @Test
    void registerCreatesOwnerAndDoesNotReturnAToken() throws Exception {
        when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantName":"Acme","tenantSlug":"acme","email":"Ada@Example.com","password":"%s","displayName":"Ada","tenantId":"999"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.tenant.slug").value("acme"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertNotEquals(PASSWORD, saved.getValue().getPasswordHash());
        assertTrue(encoder.matches(PASSWORD, saved.getValue().getPasswordHash()));
        assertTrue(saved.getValue().getTenant().getSlug().equals("acme"));
    }

    @Test
    void duplicateTenantIsRejected() throws Exception {
        Tenant existing = tenant(3L, "acme");
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantName":"Other","tenantSlug":"acme","email":"ada@example.com","password":"%s","displayName":"Ada"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource already exists"));
        verify(users, never()).save(any());
    }

    @Test
    void loginIssuesBearerTokenForVerifiedUser() throws Exception {
        Tenant tenant = tenant(7L, "acme");
        User user = user(tenant, "ada@example.com", UserRole.OWNER, true);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(users.findByTenant_IdAndEmail(7L, "ada@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantSlug":"acme","email":"Ada@Example.com","password":"%s"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void unknownUserAndBadPasswordLookTheSame() throws Exception {
        Tenant tenant = tenant(7L, "acme");
        User user = user(tenant, "ada@example.com", UserRole.OWNER, true);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(users.findByTenant_IdAndEmail(7L, "ada@example.com")).thenReturn(Optional.of(user));

        String badPassword = loginBody("acme", "ada@example.com", "wrong-password-value");
        String unknownUser = loginBody("acme", "missing@example.com", PASSWORD);
        assertTrue(badPassword.contains("Authentication failed"));
        assertTrue(unknownUser.contains("Authentication failed"));
        assertFalse(badPassword.contains(PASSWORD));
        assertFalse(badPassword.contains("wrong-password-value"));
        assertFalse(unknownUser.contains("missing@example.com"));
    }

    @Test
    void unverifiedAccountCannotLogIn() throws Exception {
        Tenant tenant = tenant(7L, "acme");
        User user = user(tenant, "ada@example.com", UserRole.OWNER, false);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(users.findByTenant_IdAndEmail(7L, "ada@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("acme", "ada@example.com", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email verification required"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void invalidAndExpiredTokensAreRejected() throws Exception {
        String invalid = mockMvc.perform(get("/api/v1/notification-preference").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(invalid.contains(SECRET));

        String expiredToken = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .subject("550e8400-e29b-41d4-a716-446655440000")
                                .issuedAt(Instant.now().minus(Duration.ofMinutes(5)))
                                .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                                .build()))
                .getTokenValue();
        String expired = mockMvc.perform(get("/api/v1/notification-preference")
                        .header(AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(expired.contains(SECRET));
        assertFalse(expired.contains(expiredToken));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/deployments/550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(ORIGIN, "http://localhost:5173")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(ORIGIN, "http://evil.example")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void workerPathRejectsUserJwt() throws Exception {
        Tenant tenant = tenant(7L, "acme");
        User user = user(tenant, "ada@example.com", UserRole.OWNER, true);
        asUser(user, get("/api/v1/worker/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/api/v1/worker/jobs").header(AUTHORIZATION, "Worker test-worker-token"))
                .andExpect(status().isNotFound());
    }

    private String loginBody(String slug, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(slug, email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String loginJson(String slug, String email, String password) {
        return """
                {"tenantSlug":"%s","email":"%s","password":"%s"}
                """
                .formatted(slug, email, password);
    }
}
