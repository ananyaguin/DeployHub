package com.cloudflow.dto.response;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseExposureTest {

    @Test
    void responsesOmitCredentialsAndEntities() {
        List<Class<?>> responses = List.of(
                ApiError.class,
                HealthResponse.class,
                TenantResponse.class,
                UserResponse.class,
                DeploymentResponse.class,
                DeploymentHealthResponse.class,
                DeploymentVersionResponse.class,
                ExecutionResponse.class,
                ExecutionLogResponse.class,
                WorkerNodeResponse.class,
                WorkerTaskResponse.class,
                NotificationPreferenceResponse.class,
                NotificationDeliveryResponse.class,
                OperationsResponse.class,
                DeploymentEventResponse.class,
                RegisterResponse.class,
                LoginResponse.class,
                PageResponse.class);
        for (Class<?> response : responses) {
            assertTrue(response.isRecord());
            for (RecordComponent component : response.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertFalse(name.contains("password"), name);
                assertFalse(name.contains("hash"), name);
                assertFalse(name.equals("token"), name);
                assertFalse(name.contains("secret"), name);
                assertFalse(name.contains("credential"), name);
                Class<?> type = component.getType();
                assertFalse(
                        type.getPackageName().equals("com.cloudflow.entity") && !type.isEnum(),
                        component.getName());
            }
        }
    }
}
