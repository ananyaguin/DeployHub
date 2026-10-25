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
    private final SimulatedTaskExecutor simulator;
    private volatile boolean running = true;
    private volatile boolean registered;
    private Thread thread;

    public WorkerLoop(WorkerAgentProperties properties, WorkerApi api, SimulatedTaskExecutor simulator) {
        this.properties = properties;
        this.api = api;
        this.simulator = simulator;
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
        api.claim(properties.name()).ifPresent(this::runTask);
    }

    private void runTask(ClaimedTask task) {
        try {
            simulator.execute(task.taskType());
            api.complete(properties.name(), task.publicId(), true, simulator.successDetail(task.taskType()));
        } catch (RuntimeException ex) {
            api.complete(properties.name(), task.publicId(), false, simulator.failureDetail(task.taskType()));
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
