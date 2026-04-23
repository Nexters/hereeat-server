package com.yogieat.batch.sync.scheduler;

import com.yogieat.batch.sync.worker.ReservationBackfillProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reservation.backfill.schedule-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReservationBackfillDailyScheduler {

    private final ReservationBackfillProcessor reservationBackfillProcessor;

    @Scheduled(
            cron = "${reservation.backfill.daily-cron:0 30 3 * * ?}",
            zone = "${reservation.backfill.zone:Asia/Seoul}"
    )
    public void processDaily() {
        try {
            reservationBackfillProcessor.process("daily-scheduled");
        } catch (Exception exception) {
            log.warn("Failed to process daily reservation backfill", exception);
        }
    }
}
