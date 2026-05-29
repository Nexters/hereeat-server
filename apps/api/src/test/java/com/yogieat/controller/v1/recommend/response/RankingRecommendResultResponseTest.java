package com.yogieat.controller.v1.recommend.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.domain.result.RecommendResultData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingRecommendResultResponseTest {

    @Test
    @DisplayName("추천 랭킹 응답은 맛집 객체에 팀 추천 문구를 포함한다")
    void from_ShouldIncludeTeamRecommendationFieldsInRestaurantObject() {
        RecommendResultData.Ranking ranking = RecommendResultData.Ranking.of(
                1,
                10L,
                "맛집 이름",
                "서울 강남구 ...",
                4.5,
                "https://img.example.com/restaurant.jpg",
                "https://place.map.kakao.com/10",
                "대표 리뷰",
                "맛집 설명",
                Region.fromString("GANGNAM"),
                new GeoJson.Point(List.of(127.0, 37.0)),
                LargeCategory.KOREAN,
                "한식",
                DistanceRange.ANY,
                120,
                30,
                "된장찌개",
                10000,
                "MEDIUM",
                "AI 요약 제목",
                List.of("요약1", "요약2"),
                "요기잇 개발자 픽",
                "여기 정말 가봤는데, 된장찌개가 맛있어요",
                "선호도가 높아 추천해요"
        );

        RankingRecommendResultResponse response = RankingRecommendResultResponse.from(ranking);

        assertThat(response.restaurant())
                .extracting(
                        RankingRecommendResultResponse.RecommendedRestaurant::restaurantId,
                        RankingRecommendResultResponse.RecommendedRestaurant::restaurantName,
                        RankingRecommendResultResponse.RecommendedRestaurant::teamRecommendationTitle,
                        RankingRecommendResultResponse.RecommendedRestaurant::teamRecommendationReason
                )
                .containsExactly(
                        10L,
                        "맛집 이름",
                        "요기잇 개발자 픽",
                        "여기 정말 가봤는데, 된장찌개가 맛있어요"
                );
    }
}
