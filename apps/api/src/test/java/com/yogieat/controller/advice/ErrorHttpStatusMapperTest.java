package com.yogieat.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorHttpStatusMapperTest {

    private final ErrorHttpStatusMapper mapper = new ErrorHttpStatusMapper();

    @Test
    void shouldMapAllErrorCodes() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(mapper.toHttpStatus(errorCode)).isNotNull();
        }
    }

    @Test
    void shouldMapRepresentativeStatuses() {
        assertThat(mapper.toHttpStatus(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.toHttpStatus(ErrorCode.METHOD_NOT_ALLOWED)).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(mapper.toHttpStatus(ErrorCode.USER_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapper.toHttpStatus(ErrorCode.KAKAO_RATE_LIMIT_EXCEEDED)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(mapper.toHttpStatus(ErrorCode.INTERNAL_SERVER_ERROR)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
