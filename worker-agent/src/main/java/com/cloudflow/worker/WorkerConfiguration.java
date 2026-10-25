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
    SimulatedTaskExecutor simulatedTaskExecutor() {
        return SimulatedTaskExecutor.standard();
    }
}
