package com.yogieat.reservation.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuckDuckGoSearchScorerTest {

    private final DuckDuckGoSearchScorer scorer = new DuckDuckGoSearchScorer();

    @Test
    @DisplayName("식당명과 지역 토큰이 모두 맞으면 임계치 이상 점수를 준다")
    void score_ShouldReturnHighScore_WhenNameAndAreaMatch() {
        double score = scorer.score(
                "한샘 분당점",
                "경기 성남시 분당구 수내동 21-2",
                "한샘 분당점 - 네이버 예약",
                "경기 성남시 분당구 수내동 21-2 무료상담",
                "https://booking.naver.com/booking/6/bizes/591723"
        );

        assertThat(score).isGreaterThanOrEqualTo(0.8d);
    }

    @Test
    @DisplayName("식당명 일치가 없으면 점수는 0이다")
    void score_ShouldReturnZero_WhenRestaurantNameDoesNotMatch() {
        double score = scorer.score(
                "한샘 분당점",
                "경기 성남시 분당구 수내동 21-2",
                "다른 식당",
                "경기 성남시 분당구 수내동 21-2",
                "https://booking.naver.com/booking/6/bizes/591723"
        );

        assertThat(score).isZero();
    }
}
