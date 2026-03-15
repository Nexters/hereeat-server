package com.yogieat.recommend.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.value.RecommendStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 추천 관련 검증 컴포넌트 */
@Component
@RequiredArgsConstructor
public class RecommendValidator {

    private final RecommendRerollPolicy recommendRerollPolicy;

    /**
     * 과반수 인원 충족 여부 검증
     *
     * @param currentCount 현재 참여자 수
     * @param peopleCount 모임 정원
     * @throws CustomException PARTICIPANT_MAJORITY_NOT_REACHED - 과반수 미달 시
     */
    public void validateMajorityReached(long currentCount, int peopleCount) {
        if (currentCount * 2 <= peopleCount) {
            throw new CustomException(ErrorCode.PARTICIPANT_MAJORITY_NOT_REACHED);
        }
    }

    /**
     * 추천 중복 진행 여부 검증
     *
     * @param alreadyExists 추천 결과 존재 여부
     * @throws CustomException RECOMMEND_ALREADY_PROCEEDED - 이미 추천 진행 중 또는 완료된 경우
     */
    public void validateNotAlreadyProceeded(boolean alreadyExists) {
        if (alreadyExists) {
            throw new CustomException(ErrorCode.RECOMMEND_ALREADY_PROCEEDED);
        }
    }

    /**
     * 재추천 가능 여부 검증
     *
     * @param recommendResults 모임의 추천 결과 목록
     * @throws CustomException RECOMMEND_RESULT_NOT_FOUND - 추천 결과가 전혀 없는 경우
     * @throws CustomException RECOMMEND_REROLL_NOT_AVAILABLE - 추천이 완료되지 않은 경우
     */
    public void validateRerollAvailable(List<RecommendResult> recommendResults) {
        if (recommendResults == null || recommendResults.isEmpty()) {
            throw new CustomException(ErrorCode.RECOMMEND_RESULT_NOT_FOUND);
        }

        RecommendStatus status = recommendResults.getFirst().status();
        if (status != RecommendStatus.COMPLETED) {
            throw new CustomException(ErrorCode.RECOMMEND_REROLL_NOT_AVAILABLE);
        }
    }

    /**
     * 재추천 횟수 제한 검증
     *
     * @param rerollCount 현재까지 재추천 이력 수
     * @throws CustomException RECOMMEND_REROLL_LIMIT_EXCEEDED - 허용 횟수 초과 시
     */
    public void validateRerollLimit(long rerollCount) {
        if (recommendRerollPolicy.isRerollLimitExceeded(rerollCount)) {
            throw new CustomException(
                    ErrorCode.RECOMMEND_REROLL_LIMIT_EXCEEDED,
                    String.format("재추천은 최대 %d회까지 가능합니다", recommendRerollPolicy.maxRerollCount())
            );
        }
    }
}
