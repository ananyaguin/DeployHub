package com.cloudflow.dto.request;

import java.util.Set;
import java.util.stream.Collectors;

import com.cloudflow.entity.DeploymentStatus;
import com.cloudflow.entity.TaskType;
import com.cloudflow.entity.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private static final String SHORT_PASSWORD = "s3cret";
    private static final String TOKEN = "raw-reset-token-value";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidNamesEmailsLengthsAndIdentifiers() {
        assertFields(
                new CreateTenantRequest(" ", "Bad Slug"),
                "name",
                "slug");
        assertFields(
                new CreateUserRequest("not-an-email", SHORT_PASSWORD, "n".repeat(121), null),
                "email",
                "password",
                "displayName",
                "role");
        assertFields(
                new CreateDeploymentRequest("n".repeat(121), "a".repeat(81)),
                "name",
                "slug");
        assertFields(
                new CreateExecutionRequest("not-a-uuid", "also-bad", null, null, null),
                "deploymentId",
                "versionId",
                "taskType");
        assertFields(new PasswordResetConfirmRequest(TOKEN, "short"), "token", "password");
        assertMessagesOmit(
                new CreateUserRequest("not-an-email", SHORT_PASSWORD, "Ada", UserRole.MEMBER), SHORT_PASSWORD);
        assertMessagesOmit(new PasswordResetConfirmRequest(TOKEN, "12345678"), TOKEN);
        assertMessagesOmit(new CreateExecutionRequest("not-a-uuid", id(), TaskType.DEPLOY, null, null), "not-a-uuid");
        assertFields(new CreateExecutionRequest(id(), id(), TaskType.DEPLOY, 0, 0), "cpuMillis", "memoryMb");
        assertFields(new WorkerNameRequest("worker-1", 16_001, 65_537), "cpuMillis", "memoryMb");
    }

    @Test
    void rejectsInvalidConfigurationAndPages() {
        assertFields(new UpdateDeploymentRequest(" ", null), "name", "status");
        assertFields(new UpdateNotificationPreferenceRequest(null), "emailEnabled");
        assertFields(new PageQuery(-1, 0), "page", "size");
        assertFields(new PageQuery(0, 101), "size");
        assertFields(new PageQuery(null, null), "page", "size");
    }

    @Test
    void acceptsValuesThatFitTheSchema() {
        assertTrue(validator.validate(new CreateTenantRequest("Acme", "acme")).isEmpty());
        assertTrue(validator
                .validate(new CreateUserRequest("ada@example.com", "12345678", "Ada", UserRole.MEMBER))
                .isEmpty());
        assertTrue(validator.validate(new CreateDeploymentRequest("API", "api")).isEmpty());
        assertTrue(validator
                .validate(new UpdateDeploymentRequest("API", DeploymentStatus.ACTIVE))
                .isEmpty());
        assertTrue(validator.validate(new CreateExecutionRequest(id(), id(), TaskType.ROLLBACK, null, null)).isEmpty());
        assertTrue(validator.validate(new CreateExecutionRequest(id(), id(), TaskType.DEPLOY, 100, 64)).isEmpty());
        assertTrue(validator.validate(new WorkerNameRequest("worker-1", null, null)).isEmpty());
        assertTrue(validator.validate(new UpdateNotificationPreferenceRequest(false)).isEmpty());
        assertTrue(validator.validate(new PageQuery(0, 20)).isEmpty());
        assertTrue(validator
                .validate(new PasswordResetConfirmRequest("a".repeat(32), "12345678"))
                .isEmpty());
    }

    private void assertFields(Object request, String... fields) {
        Set<String> actual = paths(request);
        for (String field : fields) {
            assertTrue(actual.contains(field), actual.toString());
        }
    }

    private void assertMessagesOmit(Object request, String secret) {
        Set<ConstraintViolation<Object>> violations = validate(request);
        assertFalse(violations.isEmpty());
        for (ConstraintViolation<Object> violation : violations) {
            assertFalse(violation.getMessage().contains(secret));
        }
    }

    private Set<String> paths(Object request) {
        return validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private Set<ConstraintViolation<Object>> validate(Object request) {
        return (Set<ConstraintViolation<Object>>) (Set<?>) validator.validate(request);
    }

    private static String id() {
        return "550e8400-e29b-41d4-a716-446655440000";
    }
}
