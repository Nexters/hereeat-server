package com.yogieat.controller.advice;

import com.yogieat.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorHttpStatusMapper {

    public HttpStatus toHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case SAMPLE_ERROR,
                 METHOD_ARGUMENT_TYPE_MISMATCH,
                 INVALID_LOCATION_NAME,
                 INVALID_CATEGORY_NAME,
                 GATHERING_DELETED,
                 GATHERING_FULL,
                 GATHERING_PEOPLE_COUNT_REQUIRED,
                 GATHERING_PEOPLE_COUNT_OUT_OF_RANGE,
                 GATHERING_SCHEDULED_DATE_REQUIRED,
                 GATHERING_SCHEDULED_DATE_PAST,
                 PARTICIPANT_DISLIKES_EXCEEDED,
                 PARTICIPANT_PREFERENCES_EXCEEDED,
                 PARTICIPANT_NICKNAME_REQUIRED,
                 PARTICIPANT_NICKNAME_TOO_LONG,
                 PARTICIPANT_NICKNAME_INVALID,
                 PARTICIPANT_MAJORITY_NOT_REACHED,
                 INVALID_CATEGORY_AGGREGATION,
                 INVALID_PREFERENCE_SCORE -> HttpStatus.BAD_REQUEST;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case USER_NOT_FOUND,
                 CATEGORY_NOT_FOUND,
                 RESTAURANT_NOT_FOUND,
                 SYNC_JOB_NOT_FOUND,
                 GATHERING_NOT_FOUND,
                 RECOMMEND_RESULT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GEMINI_API_ERROR,
                 KAKAO_API_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case DUPLICATE_NICKNAME,
                 SYNC_JOB_CONFLICT,
                 RECOMMEND_ALREADY_PROCEEDED,
                 RECOMMEND_REROLL_NOT_AVAILABLE,
                 RECOMMEND_REROLL_LIMIT_EXCEEDED -> HttpStatus.CONFLICT;
            case KAKAO_RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case LOCK_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
