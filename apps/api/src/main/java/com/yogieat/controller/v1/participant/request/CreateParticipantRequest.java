package com.yogieat.controller.v1.participant.request;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.participant.domain.command.ParticipantCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.regex.Pattern;

@Schema(description = "모임 참여 요청 정보")
public record CreateParticipantRequest(
        @Schema(description = "모임 accessKey", example = "access-key")
        String accessKey,
        @Schema(description = "참여자 닉네임 (최대 8자)", example = "철수")
        String nickname,
        @Schema(description = "허용 거리 (km)", example = "0.5")
        Double distance,
        @Schema(description = "참여자 불호 음식 목록", example = "[\"중식\"]")
        List<String> dislikes,
        @Schema(description = "참여자 선호 음식 목록", example = "[\"한식\", \"일식\"]")
        List<String> preferences
) {
    private static final int MAX_DISLIKES_SIZE = 2;
    private static final int MAX_PREFERENCES_SIZE = 3;
    private static final int MAX_NICKNAME_LENGTH = 8;
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$");

    /**
     * Compact constructor for validation.
     * nickname: 최대 8자 (공백 포함), dislikes: 최소 1개, 최대 4개, preferences: 최대 3개까지만 허용
     */
    public CreateParticipantRequest {
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

        if (dislikes == null || dislikes.isEmpty() || dislikes.size() > MAX_DISLIKES_SIZE) {
            throw new CustomException(ErrorCode.PARTICIPANT_DISLIKES_EXCEEDED);
        }

        if (preferences != null && preferences.size() > MAX_PREFERENCES_SIZE) {
            throw new CustomException(ErrorCode.PARTICIPANT_PREFERENCES_EXCEEDED);
        }
    }

    public static CreateParticipantRequest of(
            String accessKey,
            String nickname,
            Double distance,
            List<String> dislikes,
            List<String> preferences
    ) {
        return new CreateParticipantRequest(
                accessKey,
                nickname,
                distance,
                dislikes,
                preferences
        );
    }

    public ParticipantCommand.Create toCommand() {
        return ParticipantCommand.Create.of(
                accessKey,
                nickname,
                distance,
                dislikes,
                preferences
        );
    }
}
