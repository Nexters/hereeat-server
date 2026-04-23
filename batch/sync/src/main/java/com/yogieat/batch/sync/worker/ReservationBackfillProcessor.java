package com.yogieat.batch.sync.worker;

import com.yogieat.batch.sync.config.ReservationBackfillProperties;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.reservation.service.RestaurantReservationBackfillResult;
import com.yogieat.restaurant.reservation.service.RestaurantReservationBackfillService;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationBackfillProcessor {

    private final ReservationBackfillProperties properties;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantReservationBackfillService restaurantReservationBackfillService;
    private final ExecutorService syncJobExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RestaurantReservationBackfillResult process(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Reservation backfill skipped because another run is already active: trigger={}", trigger);
            return RestaurantReservationBackfillResult.empty();
        }

        try {
            return processInternal(trigger);
        } finally {
            running.set(false);
        }
    }

    private RestaurantReservationBackfillResult processInternal(String trigger) {
        long totalActiveRestaurants = restaurantRepository.countActiveRestaurants();
        long remaining = properties.resolvedMaxRestaurants(totalActiveRestaurants);
        Long lastRestaurantId = null;
        RestaurantReservationBackfillResult totalResult = RestaurantReservationBackfillResult.empty();

        log.info("Starting reservation backfill: trigger={}, totalActiveRestaurants={}, targetCount={}, chunkSize={}, parallelism={}",
                trigger, totalActiveRestaurants, remaining, properties.resolvedChunkSize(), properties.resolvedParallelism());

        while (remaining > 0) {
            int limit = (int) Math.min(properties.resolvedChunkSize(), remaining);
            List<Long> restaurantIds = restaurantRepository.findActiveRestaurantIdsAfter(lastRestaurantId, limit);
            if (restaurantIds.isEmpty()) {
                break;
            }

            RestaurantReservationBackfillResult chunkResult = backfillChunk(restaurantIds);
            totalResult = totalResult.plus(chunkResult);
            lastRestaurantId = restaurantIds.getLast();
            remaining -= restaurantIds.size();

            log.info("Reservation backfill chunk complete: trigger={}, lastRestaurantId={}, processed={}, created={}, updated={}, skippedProtected={}, noMatch={}",
                    trigger,
                    lastRestaurantId,
                    chunkResult.processedRestaurants(),
                    chunkResult.createdReservations(),
                    chunkResult.updatedReservations(),
                    chunkResult.skippedProtectedReservations(),
                    chunkResult.noMatchReservations());
        }

        log.info("Reservation backfill finished: trigger={}, processed={}, created={}, updated={}, skippedProtected={}, noMatch={}",
                trigger,
                totalResult.processedRestaurants(),
                totalResult.createdReservations(),
                totalResult.updatedReservations(),
                totalResult.skippedProtectedReservations(),
                totalResult.noMatchReservations());

        return totalResult;
    }

    private RestaurantReservationBackfillResult backfillChunk(List<Long> restaurantIds) {
        List<Restaurant> restaurants = restaurantRepository.findByIds(restaurantIds);
        if (restaurants.isEmpty()) {
            return RestaurantReservationBackfillResult.empty();
        }

        Semaphore semaphore = new Semaphore(properties.resolvedParallelism());
        List<CompletableFuture<RestaurantReservationBackfillResult>> futures = restaurants.stream()
                .map(restaurant -> CompletableFuture.supplyAsync(
                        () -> backfillWithParallelismLimit(semaphore, restaurant),
                        syncJobExecutor
                ))
                .toList();

        RestaurantReservationBackfillResult result = RestaurantReservationBackfillResult.empty();
        for (CompletableFuture<RestaurantReservationBackfillResult> future : futures) {
            result = result.plus(joinBackfillResult(future));
        }

        return result;
    }

    private RestaurantReservationBackfillResult backfillWithParallelismLimit(
            Semaphore semaphore,
            Restaurant restaurant
    ) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return restaurantReservationBackfillService.backfillOneRestaurant(restaurant);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RestaurantReservationBackfillResult.empty();
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private RestaurantReservationBackfillResult joinBackfillResult(
            CompletableFuture<RestaurantReservationBackfillResult> future
    ) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Reservation backfill chunk task failed", cause);
        }
    }
}
