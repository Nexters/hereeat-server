package com.yogieat.batch.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sync.job")
public record SyncJobProperties(
        Integer chunkSize,
        Integer parallelism,
        Long pollDelayMs,
        String weeklyCron,
        String weeklyZone
) {
    public int resolvedChunkSize() {
        return chunkSize == null ? 50 : chunkSize;
    }

    public int resolvedParallelism() {
        return parallelism == null ? 4 : parallelism;
    }

    public long resolvedPollDelayMs() {
        return pollDelayMs == null ? 5000L : pollDelayMs;
    }

    public String resolvedWeeklyCron() {
        return weeklyCron == null || weeklyCron.isBlank() ? "0 0 21 ? * SUN" : weeklyCron;
    }

    public String resolvedWeeklyZone() {
        return weeklyZone == null || weeklyZone.isBlank() ? "Asia/Seoul" : weeklyZone;
    }
}
