package com.yogieat.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorHttpStatusMapper());

    @Test
    @DisplayName("CustomException의 커스텀 메시지가 응답 본문에 반영된다")
    void handleCustomException_ShouldUseCustomMessage() {
        ResponseEntityWrapper response = ResponseEntityWrapper.from(
                handler.handleCustomException(
                        new CustomException(ErrorCode.RECOMMEND_REROLL_NOT_AVAILABLE, "재추천은 완료된 추천만 가능합니다")
                )
        );

        assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.error().errorCode()).isEqualTo(ErrorCode.RECOMMEND_REROLL_NOT_AVAILABLE.getCode());
        assertThat(response.error().message()).isEqualTo("재추천은 완료된 추천만 가능합니다");
    }

    @Test
    @DisplayName("역직렬화 중 발생한 CustomException도 커스텀 메시지를 유지한다")
    void handleHttpMessageNotReadable_ShouldUseCustomMessageFromCause() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "bad request",
                new CustomException(ErrorCode.RECOMMEND_RESULT_NOT_FOUND, "추천 결과가 없는 모임입니다"),
                new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8))
        );

        ResponseEntityWrapper response = ResponseEntityWrapper.from(
                handler.handleHttpMessageNotReadable(
                        exception,
                        new HttpHeaders(),
                        HttpStatus.BAD_REQUEST,
                        mock(WebRequest.class)
                )
        );

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.error().errorCode()).isEqualTo(ErrorCode.RECOMMEND_RESULT_NOT_FOUND.getCode());
        assertThat(response.error().message()).isEqualTo("추천 결과가 없는 모임입니다");
    }

    private record ResponseEntityWrapper(HttpStatus status, ErrorResponse error) {
        private static ResponseEntityWrapper from(org.springframework.http.ResponseEntity<?> responseEntity) {
            GlobalApiResponse body = (GlobalApiResponse) responseEntity.getBody();
            return new ResponseEntityWrapper(
                    HttpStatus.valueOf(responseEntity.getStatusCode().value()),
                    (ErrorResponse) body.data()
            );
        }
    }
}
