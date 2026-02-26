package com.yogieat.participant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Participant 관련 검증 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class ParticipantValidator {
    private final ParticipantService participantService;

    /**
     * 같은 모임 내 닉네임 중복 검증
     *
     * @param gatheringId 모임 ID
     * @param nickname    검증할 닉네임
     * @throws CustomException DUPLICATE_NICKNAME - 닉네임이 이미 존재할 때
     */
    public void validateNicknameDuplicate(Long gatheringId, String nickname) {
        if (nickname != null && participantService.existsByGatheringIdAndNickname(gatheringId, nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
