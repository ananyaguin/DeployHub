package com.cloudflow.worker;

public interface TaskHandler {

    String taskType();

    void execute();
}

final class DeployTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "DEPLOY";
    }

    @Override
    public void execute() {
        // SimulatedTaskExecutor owns the outcome. This handler has no container runtime.
    }
}

final class RollbackTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "ROLLBACK";
    }

    @Override
    public void execute() {
        // SimulatedTaskExecutor owns the outcome. This handler has no container runtime.
    }
}

final class StopTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "STOP";
    }

    @Override
    public void execute() {
        // SimulatedTaskExecutor owns the outcome. This handler has no container runtime.
    }
}
