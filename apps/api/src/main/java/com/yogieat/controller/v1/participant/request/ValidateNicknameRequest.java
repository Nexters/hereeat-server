package com.yogieat.controller.v1.participant.request;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.regex.Pattern;

@Schema(description = "닉네임 중복 사전 검증 요청 정보")
public record ValidateNicknameRequest(
        @Schema(description = "모임 accessKey", example = "access-key")
        String accessKey,
        @Schema(description = "참여자 닉네임 (최대 8자)", example = "철수")
        String nickname
) {
    private static final int MAX_NICKNAME_LENGTH = 8;
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$");

    public ValidateNicknameRequest {
        if (nickname == null || nickname.isBlank()) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_REQUIRED);
        }

        nickname = nickname.strip();

        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_TOO_LONG);
        }

        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new CustomException(ErrorCode.PARTICIPANT_NICKNAME_INVALID);
        }
    }
}
