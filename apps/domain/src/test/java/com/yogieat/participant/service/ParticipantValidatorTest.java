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

    @Test
    @DisplayName("닉네임이 중복되면 예외가 발생한다")
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
    @DisplayName("닉네임이 null이면 검증 없이 통과한다")
    void validateNicknameDuplicate_WhenNicknameIsNull_ShouldNotThrowException() {
        // Given
        Long gatheringId = 1L;
        String nickname = null;

        // When & Then
        assertThatCode(() -> participantValidator.validateNicknameDuplicate(gatheringId, nickname))
                .doesNotThrowAnyException();
    }
}
