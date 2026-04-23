package com.yogieat.batch.sync.runner;

import com.yogieat.batch.sync.worker.ReservationBackfillProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reservation.backfill.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReservationBackfillRunner implements ApplicationRunner {

    private final ReservationBackfillProcessor reservationBackfillProcessor;

    @Override
    public void run(ApplicationArguments args) {
        reservationBackfillProcessor.process("startup");
    }
}
