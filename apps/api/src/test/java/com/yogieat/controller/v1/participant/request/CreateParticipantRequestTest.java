package com.yogieat.controller.v1.participant.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateParticipantRequestTest {

    @Test
    @DisplayName("정상적인 요청은 생성에 성공한다")
    void validRequest_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = List.of("치킨", "피자", "햄버거");

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.accessKey()).isEqualTo(accessKey);
        assertThat(request.distance()).isEqualTo(distance);
        assertThat(request.dislikes()).hasSize(1);
        assertThat(request.preferences()).hasSize(3);
    }

    @Test
    @DisplayName("dislikes가 null이면 생성에 성공한다")
    void dislikesNull_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = null;
        List<String> preferences = List.of("치킨");

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.dislikes()).isNull();
    }

    @Test
    @DisplayName("preferences가 null이면 생성에 성공한다")
    void preferencesNull_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = null;

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.preferences()).isNull();
    }

    @Test
    @DisplayName("dislikes가 빈 리스트이면 생성에 성공한다")
    void dislikesEmpty_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of();
        List<String> preferences = List.of("치킨");

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.dislikes()).isEmpty();
    }

    @Test
    @DisplayName("preferences가 빈 리스트이면 생성에 성공한다")
    void preferencesEmpty_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = List.of();

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.preferences()).isEmpty();
    }

    @Test
    @DisplayName("dislikes가 1개이면 생성에 성공한다")
    void dislikesOne_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = List.of("치킨");

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.dislikes()).hasSize(1);
    }

    @Test
    @DisplayName("dislikes가 2개 이상이면 예외가 발생한다")
    void dislikesExceeded_shouldThrowException() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파", "마늘");
        List<String> preferences = List.of("치킨");

        // When & Then
        assertThatThrownBy(
                        () -> new CreateParticipantRequest(accessKey, distance, dislikes, preferences))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_DISLIKES_EXCEEDED);
    }

    @Test
    @DisplayName("preferences가 3개이면 생성에 성공한다")
    void preferencesThree_shouldCreateSuccessfully() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = List.of("치킨", "피자", "햄버거");

        // When
        CreateParticipantRequest request =
                new CreateParticipantRequest(accessKey, distance, dislikes, preferences);

        // Then
        assertThat(request).isNotNull();
        assertThat(request.preferences()).hasSize(3);
    }

    @Test
    @DisplayName("preferences가 4개 이상이면 예외가 발생한다")
    void preferencesExceeded_shouldThrowException() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파");
        List<String> preferences = List.of("치킨", "피자", "햄버거", "파스타");

        // When & Then
        assertThatThrownBy(
                        () -> new CreateParticipantRequest(accessKey, distance, dislikes, preferences))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_PREFERENCES_EXCEEDED);
    }

    @Test
    @DisplayName("dislikes 2개, preferences 4개 모두 초과하면 dislikes 예외가 먼저 발생한다")
    void bothExceeded_shouldThrowDislikesExceptionFirst() {
        // Given
        String accessKey = "test-access-key";
        Double distance = 500.0;
        List<String> dislikes = List.of("양파", "마늘");
        List<String> preferences = List.of("치킨", "피자", "햄버거", "파스타");

        // When & Then
        assertThatThrownBy(
                        () -> new CreateParticipantRequest(accessKey, distance, dislikes, preferences))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTICIPANT_DISLIKES_EXCEEDED);
    }
}
