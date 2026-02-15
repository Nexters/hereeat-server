package com.yogieat.batch.sync.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
@ConditionalOnProperty(
        name = "spring.task.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ScheduleConfig {
}
