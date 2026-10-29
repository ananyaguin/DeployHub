package com.cloudflow.worker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The live ping runs only when CLOUDFLOW_DOCKER_TESTS=true and a Docker socket is readable.
 * Routine CI does not set that variable. GitHub-hosted runners still have a socket.
 */
class DockerDaemonTest {

    @Test
    void missingSocketIsUnavailable() {
        DockerJavaEngine engine = new DockerJavaEngine("unix:///tmp/cloudflow-missing-docker.sock");
        assertThrows(DockerAccessException.class, engine::ping);
    }

    @Test
    @EnabledIf("socketPresent")
    @EnabledIfEnvironmentVariable(named = "CLOUDFLOW_DOCKER_TESTS", matches = "true")
    void daemonAnswersAPing() {
        DockerJavaEngine engine = new DockerJavaEngine("unix:///var/run/docker.sock");
        engine.ping();
        assertNotNull(engine);
    }

    static boolean socketPresent() {
        return Files.isReadable(Path.of("/var/run/docker.sock"));
    }
}
