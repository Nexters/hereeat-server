package com.yogieat.domain.recommend.entity;

import com.yogieat.domain.recommend.domain.RecommendResult;
import com.yogieat.domain.recommend.domain.RecommendStatus;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_recommend_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendResultEntity extends BaseEntity {

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "agreement_rate")
    private Double agreementRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecommendStatus status;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "score")
    private Double score;

    @Builder(access = AccessLevel.PRIVATE)
    public RecommendResultEntity(
            Long gatheringId,
            Long restaurantId,
            Double agreementRate,
            RecommendStatus status,
            Integer rank,
            Double score) {
        this.gatheringId = gatheringId;
        this.restaurantId = restaurantId;
        this.agreementRate = agreementRate;
        this.status = status;
        this.rank = rank;
        this.score = score;
    }

    public static RecommendResultEntity from(RecommendResult recommendResult) {
        return RecommendResultEntity.builder()
                .gatheringId(recommendResult.gatheringId())
                .restaurantId(recommendResult.restaurantId())
                .agreementRate(recommendResult.agreementRate())
                .status(recommendResult.status())
                .rank(recommendResult.rank())
                .score(recommendResult.score())
                .build();
    }

    public static RecommendResult toDomain(RecommendResultEntity entity) {
        return new RecommendResult(
                entity.getId(),
                entity.getGatheringId(),
                entity.getRestaurantId(),
                entity.getAgreementRate(),
                entity.getStatus(),
                entity.getRank(),
                entity.getScore()
        );
    }
}
