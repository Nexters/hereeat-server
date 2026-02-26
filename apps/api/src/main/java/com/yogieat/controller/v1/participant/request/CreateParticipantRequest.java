package com.yogieat.controller.v1.participant.request;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.participant.domain.command.ParticipantCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

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

    /**
     * Compact constructor for normalization and input boundary validation.
     * 닉네임 형식 검증은 ParticipantValidator에서 수행한다.
     */
    public CreateParticipantRequest {
        nickname = nickname != null ? nickname.strip() : null;

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
