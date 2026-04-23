package com.yogieat.batch.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reservation.backfill")
public record ReservationBackfillProperties(
        boolean enabled,
        boolean scheduleEnabled,
        int chunkSize,
        Integer parallelism,
        long maxRestaurants,
        String dailyCron,
        String zone
) {
    public int resolvedChunkSize() {
        return Math.max(1, chunkSize);
    }

    public long resolvedMaxRestaurants(long totalCount) {
        return maxRestaurants > 0 ? Math.min(maxRestaurants, totalCount) : totalCount;
    }

    public int resolvedParallelism() {
        if (parallelism == null) {
            return 4;
        }
        return Math.max(1, parallelism);
    }

    public String resolvedDailyCron() {
        return dailyCron == null || dailyCron.isBlank() ? "0 */5 * * * ?" : dailyCron;
    }

    public String resolvedZone() {
        return zone == null || zone.isBlank() ? "Asia/Seoul" : zone;
    }
}
