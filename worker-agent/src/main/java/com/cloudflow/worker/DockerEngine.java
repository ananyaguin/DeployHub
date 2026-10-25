package com.cloudflow.worker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DockerEngine {

    List<ContainerView> find(UUID tenantId, UUID deploymentId, int version);

    ContainerView create(ContainerSpec spec);

    void start(String containerId);

    void stop(String containerId);

    void ping();
}

record ContainerView(
        String id,
        boolean running,
        UUID tenantId,
        UUID deploymentId,
        int version,
        long memoryBytes,
        long nanoCpus,
        boolean privileged,
        boolean hostNetwork,
        int bindCount) {}

record ContainerSpec(
        String name,
        String image,
        Map<String, String> environment,
        Map<String, String> labels,
        long memoryBytes,
        long nanoCpus,
        UUID tenantId,
        UUID deploymentId,
        int version) {}
