package com.cloudflow.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WorkerLoop implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WorkerLoop.class);

    private final WorkerAgentProperties properties;
    private final WorkerApi api;
    private final DeploymentExecutor executor;
    private volatile boolean running = true;
    private volatile boolean registered;
    private Thread thread;
    /**
     * A finished task whose result has not been accepted yet. The task is not run again.
     * If this process stops first, the result is gone and the backend cannot tell whether
     * the attempt finished. Sweep may then start another attempt, up to the attempt limit.
     */
    private PendingReport pending;

    public WorkerLoop(WorkerAgentProperties properties, WorkerApi api, DeploymentExecutor executor) {
        this.properties = properties;
        this.api = api;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.token().isBlank()) {
            log.warn("WORKER_TOKEN is empty; worker loop not started");
            return;
        }
        thread = new Thread(this::loop, "worker-loop");
        thread.setDaemon(false);
        thread.start();
    }

    @Override
    public void destroy() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        while (running) {
            try {
                pollOnce();
            } catch (RuntimeException ex) {
                log.warn("Worker poll failed");
            }
            if (!sleep()) {
                return;
            }
        }
    }

    void pollOnce() {
        if (!registered) {
            api.register(properties.name());
            registered = true;
        }
        api.heartbeat(properties.name());
        if (pending != null) {
            if (settle(pending)) {
                pending = null;
            }
            return;
        }
        api.claim(properties.name()).ifPresent(this::runTask);
    }

    void runTask(ClaimedTask task) {
        TaskResult result;
        try {
            result = executor.execute(task);
        } catch (RuntimeException ex) {
            log.warn("Task execution failed");
            result = TaskResult.failed("Task failed.");
        }
        pending = new PendingReport(task, result);
        if (settle(pending)) {
            pending = null;
        }
    }

    private boolean settle(PendingReport report) {
        ReportOutcome outcome = report.send(api, properties.name());
        return switch (outcome) {
            case ACCEPTED, REJECTED -> true;
            case RETRY -> {
                log.warn("Could not report task result");
                yield false;
            }
        };
    }

    private record PendingReport(ClaimedTask task, TaskResult result) {
        private ReportOutcome send(WorkerApi api, String workerName) {
            return api.complete(
                    workerName, task.publicId(), result.succeeded(), result.message(), result.containerId());
        }
    }

    private boolean sleep() {
        try {
            Thread.sleep(properties.pollInterval().toMillis());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
