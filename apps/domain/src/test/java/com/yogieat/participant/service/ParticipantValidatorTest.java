package com.yogieat.participant.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParticipantValidatorTest {

    @Mock
    private ParticipantService participantService;

    @InjectMocks
    private ParticipantValidator participantValidator;

    // ── validateNicknameFormat ──────────────────────────────────────────────

    @Test
    @DisplayName("정상 닉네임이면 예외가 발생하지 않는다")
    void validateNicknameFormat_ValidNickname_ShouldNotThrow() {
        assertThatCode(() -> participantValidator.validateNicknameFormat("철수"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("닉네임이 null이면 PARTICIPANT_NICKNAME_REQUIRED 예외가 발생한다")
    void validateNicknameFormat_NullNickname_ShouldThrowRequired() {
        assertThatThrownBy(() -> participantValidator.validateNicknameFormat(null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NICKNAME_REQUIRED);
    }

    @Test
    @DisplayName("닉네임이 공백이면 PARTICIPANT_NICKNAME_REQUIRED 예외가 발생한다")
    void validateNicknameFormat_BlankNickname_ShouldThrowRequired() {
        assertThatThrownBy(() -> participantValidator.validateNicknameFormat("   "))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NICKNAME_REQUIRED);
    }

    @Test
    @DisplayName("닉네임이 8자를 초과하면 PARTICIPANT_NICKNAME_TOO_LONG 예외가 발생한다")
    void validateNicknameFormat_TooLongNickname_ShouldThrowTooLong() {
        assertThatThrownBy(() -> participantValidator.validateNicknameFormat("닉네임이너무길어요"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NICKNAME_TOO_LONG);
    }

    @Test
    @DisplayName("닉네임에 숫자가 포함되면 PARTICIPANT_NICKNAME_INVALID 예외가 발생한다")
    void validateNicknameFormat_NicknameWithNumber_ShouldThrowInvalid() {
        assertThatThrownBy(() -> participantValidator.validateNicknameFormat("철수123"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NICKNAME_INVALID);
    }

    @Test
    @DisplayName("닉네임에 특수문자가 포함되면 PARTICIPANT_NICKNAME_INVALID 예외가 발생한다")
    void validateNicknameFormat_NicknameWithSpecialChar_ShouldThrowInvalid() {
        assertThatThrownBy(() -> participantValidator.validateNicknameFormat("철수!"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_NICKNAME_INVALID);
    }

    // ── validateNicknameDuplicate ───────────────────────────────────────────

    @Test
    @DisplayName("닉네임이 중복되면 DUPLICATE_NICKNAME 예외가 발생한다")
    void validateNicknameDuplicate_WhenDuplicate_ShouldThrowException() {
        // Given
        Long gatheringId = 1L;
        String nickname = "테스트";
        when(participantService.existsByGatheringIdAndNickname(gatheringId, nickname)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> participantValidator.validateNicknameDuplicate(gatheringId, nickname))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("닉네임이 중복되지 않으면 예외가 발생하지 않는다")
    void validateNicknameDuplicate_WhenNotDuplicate_ShouldNotThrowException() {
        // Given
        Long gatheringId = 1L;
        String nickname = "테스트";
        when(participantService.existsByGatheringIdAndNickname(gatheringId, nickname)).thenReturn(false);

        // When & Then
        assertThatCode(() -> participantValidator.validateNicknameDuplicate(gatheringId, nickname))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("닉네임이 null이면 중복 검증 없이 통과한다")
    void validateNicknameDuplicate_WhenNicknameIsNull_ShouldNotThrowException() {
        assertThatCode(() -> participantValidator.validateNicknameDuplicate(1L, null));
    }
}
