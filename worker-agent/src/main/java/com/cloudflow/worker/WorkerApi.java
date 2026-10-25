package com.cloudflow.worker;

import java.util.Optional;
import java.util.UUID;

public interface WorkerApi {

    void register(String name);

    void heartbeat(String name);

    Optional<ClaimedTask> claim(String name);

    void complete(String name, UUID executionId, boolean succeeded, String message);
}
