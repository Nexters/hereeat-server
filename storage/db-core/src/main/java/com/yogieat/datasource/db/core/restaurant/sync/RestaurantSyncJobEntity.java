package com.yogieat.datasource.db.core.restaurant.sync;

import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "t_restaurant_sync_job",
        indexes = {
            @Index(name = "idx_sync_job_status_created_at", columnList = "status, created_at"),
            @Index(name = "idx_sync_job_trigger_created_at", columnList = "trigger_type, created_at"),
            @Index(name = "idx_sync_job_scope_status_created_at", columnList = "scope, status, created_at"),
            @Index(name = "idx_sync_job_target_status_created_at", columnList = "target_restaurant_id, status, created_at"),
            @Index(name = "idx_sync_job_status_started_at", columnList = "status, started_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantSyncJobEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantSyncScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantSyncTriggerType triggerType;

    private Long targetRestaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantSyncJobStatus status;

    @Column(nullable = false)
    private Integer chunkSize;

    @Column(nullable = false)
    private Integer parallelism;

    private Long lastProcessedRestaurantId;

    @Column(nullable = false)
    private Long totalCount;

    @Column(nullable = false)
    private Long processedCount;

    @Column(nullable = false)
    private Long successCount;

    @Column(nullable = false)
    private Long failedCount;

    @Column(columnDefinition = "TEXT")
    private String errorSummary;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RestaurantSyncJobEntity(
            RestaurantSyncScope scope,
            RestaurantSyncTriggerType triggerType,
            Long targetRestaurantId,
            RestaurantSyncJobStatus status,
            Integer chunkSize,
            Integer parallelism,
            Long lastProcessedRestaurantId,
            Long totalCount,
            Long processedCount,
            Long successCount,
            Long failedCount,
            String errorSummary,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        this.scope = scope;
        this.triggerType = triggerType;
        this.targetRestaurantId = targetRestaurantId;
        this.status = status;
        this.chunkSize = chunkSize;
        this.parallelism = parallelism;
        this.lastProcessedRestaurantId = lastProcessedRestaurantId;
        this.totalCount = totalCount;
        this.processedCount = processedCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.errorSummary = errorSummary;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static RestaurantSyncJobEntity from(RestaurantSyncJob job) {
        return RestaurantSyncJobEntity.builder()
                .scope(job.scope())
                .triggerType(job.triggerType())
                .targetRestaurantId(job.targetRestaurantId())
                .status(job.status())
                .chunkSize(job.chunkSize())
                .parallelism(job.parallelism())
                .lastProcessedRestaurantId(job.lastProcessedRestaurantId())
                .totalCount(job.totalCount())
                .processedCount(job.processedCount())
                .successCount(job.successCount())
                .failedCount(job.failedCount())
                .errorSummary(job.errorSummary())
                .startedAt(job.startedAt())
                .finishedAt(job.finishedAt())
                .build();
    }

    public static RestaurantSyncJob toDomain(RestaurantSyncJobEntity entity) {
        return new RestaurantSyncJob(
                entity.getId(),
                entity.getScope(),
                entity.getTriggerType(),
                entity.getTargetRestaurantId(),
                entity.getStatus(),
                entity.getChunkSize(),
                entity.getParallelism(),
                entity.getLastProcessedRestaurantId(),
                entity.getTotalCount(),
                entity.getProcessedCount(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                entity.getErrorSummary(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void markRunning() {
        this.status = RestaurantSyncJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorSummary = null;
    }

    public void initializeTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public void updateProgress(Long lastProcessedRestaurantId, long processedIncrement, long successIncrement, long failedIncrement) {
        this.lastProcessedRestaurantId = lastProcessedRestaurantId;
        this.processedCount += processedIncrement;
        this.successCount += successIncrement;
        this.failedCount += failedIncrement;
    }

    public void markSuccess() {
        this.status = RestaurantSyncJobStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.errorSummary = null;
    }

    public void markPartialFailed(String errorSummary) {
        this.status = RestaurantSyncJobStatus.PARTIAL_FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorSummary = errorSummary;
    }

    public void markFailed(String errorSummary) {
        this.status = RestaurantSyncJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorSummary = errorSummary;
    }
}
