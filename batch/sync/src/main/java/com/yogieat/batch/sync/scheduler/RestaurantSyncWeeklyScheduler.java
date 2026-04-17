package com.yogieat.batch.sync.scheduler;

import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantSyncWeeklyScheduler {

    private final RestaurantSyncJobService syncJobService;

    @Scheduled(
            // 매일 새벽 두시
            cron = "0 0 2 * * ?",
            zone = "${sync.job.weekly-zone:Asia/Seoul}"
    )
    public void createWeeklyAllSyncJob() {
        try {
            syncJobService.createAllJob(RestaurantSyncTriggerType.SCHEDULED);
        } catch (Exception e) {
            log.warn("Failed to create weekly sync job", e);
        }
    }
}
