package com.yogieat.controller.v1.participant.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 중복 사전 검증 요청 정보")
public record ValidateNicknameRequest(
        @Schema(description = "모임 accessKey", example = "access-key")
        String accessKey,
        @Schema(description = "참여자 닉네임 (최대 8자)", example = "철수")
        String nickname
) {
    public ValidateNicknameRequest {
        if (nickname != null) {
            nickname = nickname.strip();
        }
    }
}
