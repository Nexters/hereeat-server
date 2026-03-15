package com.yogieat.controller.v1.recommend.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.recommend.domain.command.RecommendCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProceedRecommendationRequestTest {

    @Test
    @DisplayName("toCommand는 accessKey의 앞뒤 공백을 제거한 command를 반환한다")
    void toCommand_ShouldReturnProceedCommandWithTrimmedAccessKey() {
        ProceedRecommendationRequest request = new ProceedRecommendationRequest("  access-key  ");

        RecommendCommand.Proceed command = request.toCommand();

        assertThat(command.accessKey()).isEqualTo("access-key");
    }
}
