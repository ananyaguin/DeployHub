package com.cloudflow.worker;

import java.util.List;

/**
 * Runs a claimed task inside this process. It does not call Docker, Kubernetes, or a cloud API.
 * {@code execute} returns only when the handler returns. A thrown handler is a failed task.
 */
public final class SimulatedTaskExecutor implements DeploymentExecutor {

    private final List<TaskHandler> handlers;

    public SimulatedTaskExecutor(List<TaskHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public static SimulatedTaskExecutor standard() {
        return new SimulatedTaskExecutor(
                List.of(new DeployTaskHandler(), new RollbackTaskHandler(), new StopTaskHandler()));
    }

    @Override
    public TaskResult execute(ClaimedTask task) {
        try {
            executeType(task.taskType());
            return TaskResult.ok(successDetail(task.taskType()), null);
        } catch (RuntimeException ex) {
            return TaskResult.failed(failureDetail(task.taskType()));
        }
    }

    public void executeType(String taskType) {
        for (TaskHandler handler : handlers) {
            if (handler.taskType().equals(taskType)) {
                handler.execute();
                return;
            }
        }
        throw new IllegalStateException("Unsupported task");
    }

    public String successDetail(String taskType) {
        return "Simulated " + taskType + " completed. No cloud provider was contacted.";
    }

    public String failureDetail(String taskType) {
        return "Simulated " + taskType + " failed. No cloud provider was contacted.";
    }
}
