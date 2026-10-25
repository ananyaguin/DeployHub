package com.cloudflow.worker;

public record TaskResult(boolean succeeded, String message, String containerId) {

    public static TaskResult ok(String message, String containerId) {
        return new TaskResult(true, message, containerId);
    }

    public static TaskResult failed(String message) {
        return new TaskResult(false, message, null);
    }
}
