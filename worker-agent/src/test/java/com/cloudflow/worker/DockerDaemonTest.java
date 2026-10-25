package com.cloudflow.worker;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs only when a Docker daemon socket is present. It is skipped otherwise.
 * This class does not install Docker and does not change the daemon configuration.
 */
class DockerDaemonTest {

    @Test
    @EnabledIf("socketPresent")
    void daemonAnswersAPing() {
        DockerJavaEngine engine = new DockerJavaEngine("unix:///var/run/docker.sock");
        engine.ping();
        assertNotNull(engine);
    }

    static boolean socketPresent() {
        return Files.isReadable(Path.of("/var/run/docker.sock"));
    }
}
