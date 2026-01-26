package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gathering 관련 검증 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class GatheringValidator {
    private final GatheringRepository gatheringRepository;

    /**
     * Gathering 존재 여부 및 삭제 여부 검증
     *
     * @param gatheringId 검증할 모임 ID
     * @return 검증된 Gathering 도메인
     * @throws CustomException GATHERING_NOT_FOUND - 모임이 존재하지 않을 때
     * @throws CustomException GATHERING_DELETED - 모임이 삭제되었을 때
     */
    public Gathering validateGatheringExists(Long gatheringId) {
        Gathering gathering = gatheringRepository.findById(gatheringId)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        if (gathering.isDeleted()) {
            throw new CustomException(ErrorCode.GATHERING_DELETED);
        }

        return gathering;
    }

    /**
     * Gathering 참여 인원이 가득 찼는지 검증
     *
     * @param gathering 검증할 모임
     * @param currentParticipantCount 현재 참여자 수
     * @throws CustomException GATHERING_FULL - 참여 인원이 가득 찼을 때
     */
    public void validateGatheringNotFull(Gathering gathering, long currentParticipantCount) {
        if (currentParticipantCount >= gathering.peopleCount()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }
    }
}
