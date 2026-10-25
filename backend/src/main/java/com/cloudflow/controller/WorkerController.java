package com.cloudflow.controller;

import java.util.UUID;

import com.cloudflow.dto.request.WorkerNameRequest;
import com.cloudflow.dto.request.WorkerResultRequest;
import com.cloudflow.dto.response.ExecutionResponse;
import com.cloudflow.dto.response.WorkerNodeResponse;
import com.cloudflow.dto.response.WorkerTaskResponse;
import com.cloudflow.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    private final WorkerService workers;

    public WorkerController(WorkerService workers) {
        this.workers = workers;
    }

    @PostMapping("/api/v1/worker/nodes")
    public WorkerNodeResponse register(@Valid @RequestBody WorkerNameRequest request) {
        return workers.register(request.name());
    }

    @PostMapping("/api/v1/worker/heartbeat")
    public WorkerNodeResponse heartbeat(@Valid @RequestBody WorkerNameRequest request) {
        return workers.heartbeat(request.name());
    }

    @PostMapping("/api/v1/worker/tasks/claim")
    public ResponseEntity<WorkerTaskResponse> claim(@Valid @RequestBody WorkerNameRequest request) {
        return workers.claim(request.name())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/worker/tasks/{publicId}/result")
    public ExecutionResponse result(@PathVariable UUID publicId, @Valid @RequestBody WorkerResultRequest request) {
        return workers.finish(publicId, request.name(), request.succeeded(), request.message());
    }
}
