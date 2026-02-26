package com.yogieat.participant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Participant 관련 검증 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class ParticipantValidator {

    private static final int MAX_NICKNAME_LENGTH = 8;
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$");

    private final ParticipantService participantService;

    /**
     * 닉네임 형식 검증 (null 여부, 최대 길이, 허용 문자)
     *
     * @param nickname 검증할 닉네임
     * @throws CustomException PARTICIPANT_NICKNAME_REQUIRED - 닉네임이 null 또는 공백일 때
     * @throws CustomException PARTICIPANT_NICKNAME_TOO_LONG - 닉네임이 최대 길이를 초과할 때
     * @throws CustomException PARTICIPANT_NICKNAME_INVALID - 닉네임에 허용되지 않는 문자가 포함될 때
     */
    public void validateNicknameFormat(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_REQUIRED);
        }

        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_TOO_LONG);
        }

        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_INVALID);
        }
    }

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
