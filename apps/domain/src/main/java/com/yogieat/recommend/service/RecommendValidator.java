package com.yogieat.recommend.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 추천 관련 검증 컴포넌트 */
@Component
@RequiredArgsConstructor
public class RecommendValidator {

    /**
     * 과반수 인원 충족 여부 검증
     *
     * @param currentCount 현재 참여자 수
     * @param peopleCount 모임 정원
     * @throws CustomException PARTICIPANT_MAJORITY_NOT_REACHED - 과반수 미달 시
     */
    public void validateMajorityReached(long currentCount, int peopleCount) {
        if (currentCount * 2 < peopleCount) {
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
}
