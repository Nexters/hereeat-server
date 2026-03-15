package com.yogieat.controller.v1.recommend.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.recommend.domain.command.RecommendCommand;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RerollRecommendResultRequestTest {

    @Test
    @DisplayName("toCommand는 accessKey를 trim하고 restaurantIds를 중복 제거한 command를 반환한다")
    void toCommand_ShouldReturnRerollCommandWithNormalizedValues() {
        RerollRecommendResultRequest request = new RerollRecommendResultRequest(
                "  access-key  ",
                List.of(1L, 2L, 1L, 3L, 2L)
        );

        RecommendCommand.Reroll command = request.toCommand();

        assertThat(command.accessKey()).isEqualTo("access-key");
        assertThat(command.restaurantIds()).containsExactly(1L, 2L, 3L);
    }
}
