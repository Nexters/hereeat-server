package com.yogieat.batch.sync.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SyncWorkerConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService syncJobExecutor(SyncJobProperties syncJobProperties) {
        return Executors.newFixedThreadPool(syncJobProperties.resolvedParallelism());
    }
}
