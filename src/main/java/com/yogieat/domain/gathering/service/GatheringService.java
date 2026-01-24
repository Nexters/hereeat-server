package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.domain.Gathering;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatheringService {
    private final GatheringValidator gatheringValidator;

    /**
     * Gathering 존재 여부 및 삭제 여부 검증
     * GatheringValidator에게 위임
     *
     * @param gatheringId 검증할 모임 ID
     * @return 검증된 Gathering 도메인
     */
    public Gathering validateGatheringExists(Long gatheringId) {
        return gatheringValidator.validateGatheringExists(gatheringId);
    }

    /**
     * Gathering 참여 인원이 가득 찼는지 검증
     * GatheringValidator에게 위임
     *
     * @param gathering 검증할 모임
     * @param currentParticipantCount 현재 참여자 수
     */
    public void validateGatheringNotFull(Gathering gathering, long currentParticipantCount) {
        gatheringValidator.validateGatheringNotFull(gathering, currentParticipantCount);
    }
}
