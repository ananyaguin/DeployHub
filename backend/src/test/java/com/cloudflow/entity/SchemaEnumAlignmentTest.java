package com.cloudflow.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SchemaEnumAlignmentTest {

    @Test
    void enumNamesMatchFlywayChecks() throws IOException {
        String schema = read("db/migration/V1__initial_schema.sql")
                + read("db/migration/V2__public_ids_and_task_fields.sql")
                + read("db/migration/V7__deployment_health.sql")
                + read("db/migration/V8__declared_capacity_and_notices.sql");
        assertCheck(schema, "chk_deployments_status", DeploymentStatus.values());
        assertCheck(schema, "chk_deployments_health_status", HealthStatus.values());
        assertCheck(schema, "chk_executions_status", ExecutionStatus.values());
        assertCheck(schema, "chk_worker_nodes_status", WorkerStatus.values());
        assertCheck(schema, "chk_execution_logs_level", LogLevel.values());
        assertCheck(schema, "chk_notification_deliveries_status", DeliveryStatus.values());
        assertCheck(schema, "chk_notification_deliveries_channel", NotificationChannel.values());
        assertCheck(schema, "chk_users_role", UserRole.values());
        assertCheck(schema, "chk_executions_task_type", TaskType.values());
    }

    private static void assertCheck(String schema, String constraint, Enum<?>[] values) {
        int start = schema.lastIndexOf("CONSTRAINT " + constraint);
        assertTrue(start >= 0, constraint);
        int end = schema.indexOf(')', start);
        String clause = schema.substring(start, end);
        for (Enum<?> value : values) {
            assertTrue(clause.contains("'" + value.name() + "'"), constraint + " missing " + value.name());
        }
    }

    private static String read(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
