package com.cloudflow.worker;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ClaimedTask(
        UUID publicId,
        String taskType,
        int attempt,
        UUID tenantId,
        UUID deploymentId,
        int versionNumber,
        String image,
        Map<String, String> environment,
        Integer currentVersion,
        String containerId,
        boolean configurationValid) {

    public ClaimedTask {
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public static ClaimedTask simple(UUID publicId, String taskType, int attempt) {
        return new ClaimedTask(
                publicId, taskType, attempt, UUID.randomUUID(), UUID.randomUUID(), 1, null, Map.of(), null, null, true);
    }

    static ClaimedTask parse(ObjectMapper json, String body) throws IOException {
        JsonNode node = json.readTree(body);
        Map<String, String> environment = new LinkedHashMap<>();
        JsonNode env = node.path("environment");
        if (env.isObject()) {
            env.properties().forEach(entry -> environment.put(entry.getKey(), entry.getValue().asText("")));
        }
        return new ClaimedTask(
                UUID.fromString(node.path("publicId").asText()),
                node.path("taskType").asText(),
                node.path("attempt").asInt(),
                uuid(node, "tenantId"),
                uuid(node, "deploymentId"),
                node.path("versionNumber").asInt(),
                text(node, "image"),
                environment,
                node.path("currentVersion").isNumber() ? node.path("currentVersion").asInt() : null,
                text(node, "containerId"),
                !node.has("configurationValid") || node.path("configurationValid").asBoolean());
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : UUID.fromString(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
