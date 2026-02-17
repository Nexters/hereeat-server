package com.yogieat.config.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class SseTaskExecutorConfig {

    @Bean
    public TaskExecutor sseTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("sse-heartbeat-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
