package com.yogieat.scheduler;

import com.yogieat.restaurant.service.RestaurantCollectionProcessor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantCollectionScheduler {

    private final RestaurantCollectionProcessor collectionProcessor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 3시, 6시, 9시, 12시, 15시, 18시, 21시에 맛집 정보 수집 배치 실행
     * Cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 0,3,6,9,12,15,18,21 * * ?")
    public void collectRestaurants() {
        // 중복 실행 방지: 이전 배치가 아직 실행 중이면 스킵
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }

        log.info("Starting scheduled restaurant collection");
        long startTime = System.currentTimeMillis();

        try {
            collectionProcessor.collectAllRegions();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Restaurant collection completed successfully in {} ms ({} minutes)",
                duration, duration / 60000);

        } catch (Exception e) {
            log.error("Restaurant collection failed", e);
            // 여기에 모니터링/알림 추가 고려
        } finally {
            // 실행 완료 후 플래그 해제
            isRunning.set(false);
        }
    }
}
