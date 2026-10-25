package com.cloudflow.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkerAgentProperties.class)
public class WorkerConfiguration {

    @Bean
    WorkerApi workerApi(WorkerAgentProperties properties) {
        return new WorkerHttpClient(properties);
    }

    @Bean
    DeploymentExecutor deploymentExecutor(WorkerAgentProperties properties) {
        if ("docker".equals(properties.executor())) {
            return new DockerTaskExecutor(
                    new DockerJavaEngine(properties.dockerHost()), properties.memoryBytes(), properties.nanoCpus());
        }
        return SimulatedTaskExecutor.standard();
    }
}
