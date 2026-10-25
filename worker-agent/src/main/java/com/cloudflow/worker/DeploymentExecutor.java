package com.cloudflow.worker;

public interface DeploymentExecutor {

    TaskResult execute(ClaimedTask task);
}
