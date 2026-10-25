package com.cloudflow.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.cloudflow.dto.request.PasswordResetConfirmRequest;
import com.cloudflow.dto.request.VerifyEmailRequest;
import com.cloudflow.entity.EmailVerificationToken;
import com.cloudflow.entity.PasswordResetToken;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.exception.TokenRejectedException;
import com.cloudflow.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountRecoveryTest extends SecurityTestBase {

    private static final String NEW_PASSWORD = "replacement-password";

    private final Map<String, EmailVerificationToken> verificationRows = new HashMap<>();
    private final Map<String, PasswordResetToken> resetRows = new HashMap<>();

    @Autowired
    AccountService accounts;

    @BeforeEach
    void rememberTokens() {
        verificationRows.clear();
        resetRows.clear();
        when(verifications.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> keep(verificationRows, invocation));
        when(verifications.findByTokenHash(any())).thenAnswer(invocation ->
                Optional.ofNullable(verificationRows.get(invocation.getArgument(0))));
        when(verifications.consume(any(), any())).thenAnswer(invocation -> take(verificationRows, invocation));
        when(resets.save(any(PasswordResetToken.class))).thenAnswer(invocation -> keep(resetRows, invocation));
        when(resets.findByTokenHash(any())).thenAnswer(invocation ->
                Optional.ofNullable(resetRows.get(invocation.getArgument(0))));
        when(resets.consume(any(), any())).thenAnswer(invocation -> take(resetRows, invocation));
    }

    @Test
    void verificationAcceptsOneTokenAndThenRejectsIt() throws Exception {
        User owner = register();
        String raw = lastToken();
        String body = mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(body.contains(raw));
        assertNotNull(owner.getEmailVerifiedAt());
        assertNotEquals(raw, verificationRows.values().iterator().next().getTokenHash());
        assertEquals(64, verificationRows.values().iterator().next().getTokenHash().length());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andExpect(jsonPath("$.token").doesNotExist());

        when(tenants.findBySlug("acme")).thenReturn(Optional.of(owner.getTenant()));
        when(users.findByTenant_IdAndEmail(1L, "ada@example.com")).thenReturn(Optional.of(owner));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantSlug":"acme","email":"ada@example.com","password":"%s"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void invalidExpiredAndConsumedVerificationTokensAreRejected() throws Exception {
        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, false);
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"missing-token-value-0123456789abcdef\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request could not be processed"));

        EmailVerificationToken expired =
                storedVerification(owner, "expired-token-value-0123456789abcdef", Instant.EPOCH, null);
        rejectVerification("expired-token-value-0123456789abcdef");
        assertNull(expired.getUsedAt());
        assertNull(owner.getEmailVerifiedAt());

        storedVerification(
                owner,
                "used-token-value-0123456789abcdef",
                Instant.parse("2999-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        rejectVerification("used-token-value-0123456789abcdef");
        assertNull(owner.getEmailVerifiedAt());
    }

    @Test
    void passwordResetUpdatesTheHashAndLeavesTheAccountUnverified() throws Exception {
        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, false);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(owner.getTenant()));
        when(users.findByTenant_IdAndEmail(1L, "ada@example.com")).thenReturn(Optional.of(owner));
        String oldHash = owner.getPasswordHash();

        String unknown = resetResponse("missing@example.com");
        String known = resetResponse("ada@example.com");
        assertEquals(unknown, known);
        assertFalse(known.contains("ada@example.com"));
        assertFalse(known.contains(NEW_PASSWORD));

        String raw = lastToken();
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertTrue(encoder.matches(NEW_PASSWORD, owner.getPasswordHash()));
        assertFalse(encoder.matches(PASSWORD, owner.getPasswordHash()));
        assertNotEquals(NEW_PASSWORD, owner.getPasswordHash());
        assertNotEquals(oldHash, owner.getPasswordHash());
        assertNull(owner.getEmailVerifiedAt());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\",\"password\":\"another-password\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(encoder.matches(NEW_PASSWORD, owner.getPasswordHash()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantSlug":"acme","email":"ada@example.com","password":"%s"}
                                """
                                        .formatted(NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email verification required"));
    }

    @Test
    void invalidExpiredAndConsumedResetTokensDoNotChangeThePassword() throws Exception {
        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, false);
        String hash = owner.getPasswordHash();
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"token\":\"missing-token-value-0123456789abcdef\",\"password\":\""
                                        + NEW_PASSWORD
                                        + "\"}"))
                .andExpect(status().isBadRequest());
        storedReset(owner, "expired-token-value-0123456789abcdef", Instant.EPOCH, null);
        rejectReset("expired-token-value-0123456789abcdef");
        storedReset(
                owner,
                "used-token-value-0123456789abcdef",
                Instant.parse("2999-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        rejectReset("used-token-value-0123456789abcdef");
        assertEquals(hash, owner.getPasswordHash());
        verify(users, never()).save(any());
    }

    @Test
    void unknownResetRequestMatchesAKnownAccountAndHidesMailFailure() throws Exception {
        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, true);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(owner.getTenant()));
        String unknown = resetResponse("missing@example.com");
        verify(resets, never()).save(any());
        verify(mail, never()).send(any(SimpleMailMessage.class));

        when(users.findByTenant_IdAndEmail(1L, "ada@example.com")).thenReturn(Optional.of(owner));
        doThrow(new MailSendException("smtp-secret-password")).when(mail).send(any(SimpleMailMessage.class));
        String failed = resetResponse("ada@example.com");
        assertEquals(unknown, failed);
        assertFalse(failed.contains("smtp-secret-password"));
        assertFalse(failed.contains("ada@example.com"));
    }

    @Test
    void registrationMailFailureDoesNotEchoSecrets() throws Exception {
        when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("smtp-secret-password")).when(mail).send(any(SimpleMailMessage.class));

        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantName":"Acme","tenantSlug":"acme","email":"ada@example.com","password":"%s","displayName":"Ada"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Email could not be sent"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains("smtp-secret-password"));
        assertFalse(body.contains("Exception"));
    }

    @Test
    void concurrentVerificationConsumesTheTokenOnce() throws Exception {
        User owner = register();
        String raw = lastToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> attemptVerify(start, raw));
        Future<Boolean> second = pool.submit(() -> attemptVerify(start, raw));
        start.countDown();
        assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        pool.shutdown();
        assertNotNull(owner.getEmailVerifiedAt());
    }

    @Test
    void concurrentResetConsumesTheTokenOnce() throws Exception {
        User owner = user(tenant(1L, "acme"), "ada@example.com", UserRole.OWNER, false);
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(owner.getTenant()));
        when(users.findByTenant_IdAndEmail(1L, "ada@example.com")).thenReturn(Optional.of(owner));
        resetResponse("ada@example.com");
        String raw = lastToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = pool.submit(() -> attemptReset(start, raw, "replacement-password"));
        Future<Boolean> second = pool.submit(() -> attemptReset(start, raw, "other-replacement"));
        start.countDown();
        assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        pool.shutdown();
        boolean firstMatches = encoder.matches("replacement-password", owner.getPasswordHash());
        boolean secondMatches = encoder.matches("other-replacement", owner.getPasswordHash());
        assertTrue(firstMatches ^ secondMatches);
        assertNull(owner.getEmailVerifiedAt());
    }

    private boolean attemptVerify(CountDownLatch start, String raw) throws InterruptedException {
        start.await();
        try {
            accounts.verifyEmail(new VerifyEmailRequest(raw));
            return true;
        } catch (TokenRejectedException ex) {
            return false;
        }
    }

    private boolean attemptReset(CountDownLatch start, String raw, String password) throws InterruptedException {
        start.await();
        try {
            accounts.confirmPasswordReset(new PasswordResetConfirmRequest(raw, password));
            return true;
        } catch (TokenRejectedException ex) {
            return false;
        }
    }

    private User register() throws Exception {
        when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user.getTenant(), "id", 1L);
            return user;
        });
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"tenantName":"Acme","tenantSlug":"acme","email":"ada@example.com","password":"%s","displayName":"Ada"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        String body = lastToken();
        assertFalse(body.isBlank());
        return saved.getValue();
    }

    private String resetResponse(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantSlug\":\"acme\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void rejectVerification(String raw) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andExpect(content().string(Matchers.not(Matchers.containsString(raw))));
    }

    private void rejectReset(String raw) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.not(Matchers.containsString(raw))))
                .andExpect(content().string(Matchers.not(Matchers.containsString(NEW_PASSWORD))));
    }

    private EmailVerificationToken storedVerification(User owner, String raw, Instant expiresAt, Instant usedAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(owner);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        verificationRows.put(token.getTokenHash(), token);
        return token;
    }

    private void storedReset(User owner, String raw, Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(owner);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        resetRows.put(token.getTokenHash(), token);
    }

    private String lastToken() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(message.capture());
        String text = message.getValue().getText();
        return text.substring(text.lastIndexOf("token=") + "token=".length());
    }

    private static <T> T keep(Map<String, T> rows, InvocationOnMock invocation) {
        T token = invocation.getArgument(0);
        String hash = token instanceof EmailVerificationToken verification
                ? verification.getTokenHash()
                : ((PasswordResetToken) token).getTokenHash();
        rows.put(hash, token);
        return token;
    }

    private static int take(Map<String, ?> rows, InvocationOnMock invocation) {
        String hash = invocation.getArgument(0);
        Instant now = invocation.getArgument(1);
        synchronized (rows) {
            Object token = rows.get(hash);
            if (token instanceof EmailVerificationToken verification) {
                return mark(verification.getUsedAt(), verification.getExpiresAt(), now, verification::setUsedAt);
            }
            if (token instanceof PasswordResetToken reset) {
                return mark(reset.getUsedAt(), reset.getExpiresAt(), now, reset::setUsedAt);
            }
            return 0;
        }
    }

    private static int mark(Instant usedAt, Instant expiresAt, Instant now, Consumer<Instant> consume) {
        if (usedAt != null || !expiresAt.isAfter(now)) {
            return 0;
        }
        consume.accept(now);
        return 1;
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
