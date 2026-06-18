package com.yogieat.controller.v1.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.controller.v1.recommend.request.RerollRecommendResultRequest;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendResultV1ControllerValidationTest {

    @Test
    @DisplayName("재추천 API 요청 바디 파라미터에 @Valid가 선언되어 있다")
    void rerollRequestBodyParameter_ShouldHaveValidAnnotation() throws NoSuchMethodException {
        Method method = RecommendResultV1Controller.class.getDeclaredMethod(
                "rerollRecommendResults",
                RerollRecommendResultRequest.class
        );

        Parameter parameter = method.getParameters()[0];
        assertThat(parameter.isAnnotationPresent(Valid.class)).isTrue();
    }
}
