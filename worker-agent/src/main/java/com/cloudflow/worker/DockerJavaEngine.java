package com.cloudflow.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/**
 * Docker Engine API adapter. Host paths, host networking, and privileged mode are not accepted.
 * Docker daemon access is host-level power. This class does not change the daemon's own security settings.
 */
public final class DockerJavaEngine implements DockerEngine {

    private final DockerClient client;

    public DockerJavaEngine(String dockerHost) {
        this(open(dockerHost));
    }

    private static DockerClient open(String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        DockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        return DockerClientBuilder.getInstance(config).withDockerHttpClient(http).build();
    }

    DockerJavaEngine(DockerClient client) {
        this.client = client;
    }

    @Override
    public void ping() {
        try {
            client.pingCmd().exec();
        } catch (RuntimeException ex) {
            throw new DockerAccessException();
        }
    }

    @Override
    public List<ContainerView> find(UUID tenantId, UUID deploymentId, int version) {
        try {
            List<com.github.dockerjava.api.model.Container> containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(DockerTaskExecutor.labels(tenantId, deploymentId, version))
                    .exec();
            List<ContainerView> views = new ArrayList<>();
            for (com.github.dockerjava.api.model.Container container : containers) {
                views.add(inspect(container.getId()));
            }
            return views;
        } catch (RuntimeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public ContainerView create(ContainerSpec spec) {
        try {
            pull(spec.image());
            HostConfig host = HostConfig.newHostConfig()
                    .withMemory(spec.memoryBytes())
                    .withNanoCPUs(spec.nanoCpus())
                    .withPrivileged(false)
                    .withNetworkMode("bridge")
                    .withBinds(new Bind[0])
                    .withRestartPolicy(RestartPolicy.noRestart());
            List<String> env = spec.environment().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();
            CreateContainerResponse created = client.createContainerCmd(spec.image())
                    .withName(spec.name())
                    .withEnv(env)
                    .withLabels(spec.labels())
                    .withHostConfig(host)
                    .exec();
            client.startContainerCmd(created.getId()).exec();
            return inspect(created.getId());
        } catch (RuntimeException ex) {
            throw wrap(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DockerAccessException();
        }
    }

    @Override
    public void start(String containerId) {
        try {
            client.startContainerCmd(containerId).exec();
        } catch (RuntimeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public void stop(String containerId) {
        try {
            client.stopContainerCmd(containerId).withTimeout(10).exec();
        } catch (NotFoundException ex) {
            return;
        } catch (RuntimeException ex) {
            throw wrap(ex);
        }
    }

    private void pull(String image) throws InterruptedException {
        String repository = image;
        String tag = null;
        if (image.contains("@sha256:")) {
            repository = image;
        } else {
            int slash = image.lastIndexOf('/');
            int colon = image.lastIndexOf(':');
            if (colon > slash) {
                repository = image.substring(0, colon);
                tag = image.substring(colon + 1);
            }
        }
        var command = client.pullImageCmd(repository);
        if (tag != null) {
            command.withTag(tag);
        }
        if (!command.exec(new PullImageResultCallback()).awaitCompletion(120, TimeUnit.SECONDS)) {
            throw new DockerAccessException();
        }
    }

    private ContainerView inspect(String id) {
        InspectContainerResponse inspected = client.inspectContainerCmd(id).exec();
        Map<String, String> labels = inspected.getConfig() == null || inspected.getConfig().getLabels() == null
                ? Map.of()
                : inspected.getConfig().getLabels();
        HostConfig host = inspected.getHostConfig();
        int binds = host == null || host.getBinds() == null ? 0 : host.getBinds().length;
        String network = host == null ? "" : String.valueOf(host.getNetworkMode());
        return new ContainerView(
                inspected.getId(),
                inspected.getState() != null && Boolean.TRUE.equals(inspected.getState().getRunning()),
                uuid(labels.get(DockerTaskExecutor.TENANT)),
                uuid(labels.get(DockerTaskExecutor.DEPLOYMENT)),
                version(labels.get(DockerTaskExecutor.VERSION)),
                host == null || host.getMemory() == null ? 0L : host.getMemory(),
                host == null || host.getNanoCPUs() == null ? 0L : host.getNanoCPUs(),
                host != null && Boolean.TRUE.equals(host.getPrivileged()),
                "host".equalsIgnoreCase(network),
                binds);
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int version(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static RuntimeException wrap(RuntimeException ex) {
        if (ex instanceof DockerAccessException) {
            return ex;
        }
        String name = ex.getClass().getSimpleName();
        if (name.contains("Connect") || name.contains("Timeout") || name.contains("Processing")) {
            return new DockerAccessException();
        }
        return new IllegalStateException("Docker operation failed");
    }
}
