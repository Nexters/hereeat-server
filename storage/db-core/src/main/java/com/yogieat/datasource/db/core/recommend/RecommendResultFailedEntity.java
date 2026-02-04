package com.yogieat.datasource.db.core.recommend;

import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.recommend.domain.FailureReason;
import com.yogieat.recommend.domain.RecommendResultFailed;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "t_recommend_result_failed",
    indexes = {
        @Index(name = "idx_recommend_result_failed_gathering_id", columnList = "gathering_id"),
        @Index(name = "idx_recommend_result_failed_failed_at", columnList = "failed_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendResultFailedEntity extends BaseEntity {

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", nullable = false, length = 50)
    private FailureReason failureReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Builder(access = AccessLevel.PRIVATE)
    public RecommendResultFailedEntity(
            Long gatheringId,
            FailureReason failureReason,
            String errorMessage,
            LocalDateTime failedAt
    ) {
        this.gatheringId = gatheringId;
        this.failureReason = failureReason;
        this.errorMessage = errorMessage;
        this.failedAt = failedAt;
    }

    public static RecommendResultFailedEntity from(RecommendResultFailed domain) {
        return RecommendResultFailedEntity.builder()
                .gatheringId(domain.gatheringId())
                .failureReason(domain.failureReason())
                .errorMessage(domain.errorMessage())
                .failedAt(domain.failedAt())
                .build();
    }

    public RecommendResultFailed toDomain() {
        return new RecommendResultFailed(
                this.getId(),
                this.gatheringId,
                this.failureReason,
                this.errorMessage,
                this.failedAt
        );
    }
}
