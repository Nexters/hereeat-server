package com.yogieat.domain.restaurant.scheduler;

import com.yogieat.domain.restaurant.service.RestaurantCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantCollectionScheduler {

    private final RestaurantCollectionService collectionService;

    /**
     * Restaurant collection every 10 minutes (0, 10, 20, 30, 40, 50)
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void collectRestaurants() {
        log.info("Starting scheduled restaurant collection");
        long startTime = System.currentTimeMillis();

        try {
            collectionService.collectAllRegions();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Restaurant collection completed successfully in {} ms ({} minutes)",
                duration, duration / 60000);

        } catch (Exception e) {
            log.error("Restaurant collection failed", e);
            // Consider adding monitoring/alerting here
        }
    }
}
