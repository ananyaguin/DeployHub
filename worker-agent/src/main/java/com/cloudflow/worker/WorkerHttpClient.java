package com.cloudflow.worker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkerHttpClient implements WorkerApi {

    private static final Logger log = LoggerFactory.getLogger(WorkerHttpClient.class);

    private final HttpClient http;
    private final ObjectMapper json;
    private final String backendUrl;
    private final String token;

    public WorkerHttpClient(WorkerAgentProperties properties) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), new ObjectMapper(), properties);
    }

    WorkerHttpClient(HttpClient http, ObjectMapper json, WorkerAgentProperties properties) {
        this.http = http;
        this.json = json;
        this.backendUrl = properties.backendUrl();
        this.token = properties.token();
    }

    @Override
    public void register(String name) {
        post("/api/v1/worker/nodes", body(name, null, null), false);
    }

    @Override
    public void heartbeat(String name) {
        post("/api/v1/worker/heartbeat", body(name, null, null), false);
    }

    @Override
    public Optional<ClaimedTask> claim(String name) {
        HttpResponse<String> response = post("/api/v1/worker/tasks/claim", body(name, null, null), true);
        if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parse(response.body()));
    }

    @Override
    public void complete(String name, UUID executionId, boolean succeeded, String message) {
        post("/api/v1/worker/tasks/" + executionId + "/result", body(name, succeeded, message), false);
    }

    private ObjectNode body(String name, Boolean succeeded, String message) {
        ObjectNode node = json.createObjectNode();
        node.put("name", name);
        if (succeeded != null) {
            node.put("succeeded", succeeded);
            node.put("message", message == null ? "" : message);
        }
        return node;
    }

    private HttpResponse<String> post(String path, ObjectNode body, boolean allowEmpty) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Worker " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) {
                log.warn("Worker authentication was rejected");
                throw new IllegalStateException("Worker request failed");
            }
            if (allowEmpty && status == 204) {
                return response;
            }
            if (status < 200 || status >= 300) {
                log.warn("Worker request failed with status {}", status);
                throw new IllegalStateException("Worker request failed");
            }
            return response;
        } catch (IOException ex) {
            log.warn("Worker request failed");
            throw new IllegalStateException("Worker request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker request failed");
        }
    }

    private ClaimedTask parse(String body) {
        try {
            JsonNode node = json.readTree(body);
            return new ClaimedTask(
                    UUID.fromString(node.path("publicId").asText()),
                    node.path("taskType").asText(),
                    node.path("attempt").asInt());
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Worker response could not be read");
            throw new IllegalStateException("Worker request failed");
        }
    }
}
