package com.yogieat.controller.v1.participant;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.controller.v1.participant.request.CreateParticipantRequest;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipantControllerValidationTest {

    @Test
    @DisplayName("참여 API 요청 바디 파라미터에 @Valid가 선언되어 있다")
    void requestBodyParameter_ShouldHaveValidAnnotation() throws NoSuchMethodException {
        Method method = ParticipantController.class.getDeclaredMethod(
                "participateInGathering",
                CreateParticipantRequest.class
        );

        Parameter parameter = method.getParameters()[0];
        assertThat(parameter.isAnnotationPresent(Valid.class)).isTrue();
    }
}
