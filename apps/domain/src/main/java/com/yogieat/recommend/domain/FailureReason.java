package com.yogieat.recommend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FailureReason {
    ASYNC_PROCESSING_TIMEOUT("비동기 처리 타임아웃"),
    NO_PARTICIPANTS("참여자 없음"),
    NO_RESTAURANTS("해당 지역 레스토랑 없음"),
    PROCESSING_EXCEPTION("처리 중 예외 발생");

    private final String description;
}
