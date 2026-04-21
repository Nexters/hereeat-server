package com.yogieat.kakao.kakao.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoPlaceDetailParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 테스트 기준 날짜: 2026-04-21 (KST)
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-04-20T15:00:00Z"), KST); // UTC 15:00 = KST 00:00 (4/21)
    private final KakaoPlaceDetailParser parser = new KakaoPlaceDetailParser(FIXED_CLOCK);
    private static final String PLACE_ID = "16053234";
    private static final int YEAR = 2026;

    @Test
    @DisplayName("off_days_desc가 '휴무일'인 날짜만 off_days로 수집한다")
    void parse_collectsOffDays() throws Exception {
        int year = YEAR;
        JsonNode panel = panel("""
                {
                  "summary": {
                    "category": {"name1": "음식점", "name2": "한식", "name3": "육류,고기"},
                    "name": "테스트식당",
                    "confirm_id": "16053234",
                    "address": {"road": "서울 어딘가"},
                    "point": {"lat": 37.5, "lon": 127.0}
                  },
                  "kakaomap_review": {"score_set": {"average_score": 4.0, "review_count": 10}},
                  "open_hours": {
                    "week_from_today": {
                      "week_periods": [
                        {"days": [
                          {"day_of_the_week_desc": "화(4/21)", "on_days": {"start_end_time_desc": "15:00 ~ 22:00"}},
                          {"day_of_the_week_desc": "수(4/22)", "on_days": {"start_end_time_desc": "15:00 ~ 22:00"}}
                        ]},
                        {"days": [
                          {"day_of_the_week_desc": "일(4/26)", "off_days_desc": "휴무일"}
                        ]},
                        {"days": [
                          {"day_of_the_week_desc": "월(4/27)", "on_days": {"start_end_time_desc": "15:00 ~ 22:00"}}
                        ]}
                      ]
                    }
                  }
                }
                """);

        KakaoPlaceDetailData result = parser.parse(panel, PLACE_ID);

        assertThat(result.offDays()).containsExactly(LocalDate.of(year, 4, 26));
    }

    @Test
    @DisplayName("on_days만 있고 off_days_desc가 없으면 off_days는 빈 리스트다")
    void parse_emptyOffDaysWhenAllOpen() throws Exception {
        JsonNode panel = panel("""
                {
                  "summary": {
                    "category": {"name1": "음식점", "name2": "한식", "name3": "육류,고기"},
                    "name": "테스트식당",
                    "confirm_id": "16053234",
                    "address": {"road": "서울 어딘가"},
                    "point": {"lat": 37.5, "lon": 127.0}
                  },
                  "kakaomap_review": {"score_set": {"average_score": 4.0, "review_count": 10}},
                  "open_hours": {
                    "week_from_today": {
                      "week_periods": [
                        {"days": [
                          {"day_of_the_week_desc": "화(4/21)", "on_days": {"start_end_time_desc": "15:00 ~ 22:00"}},
                          {"day_of_the_week_desc": "수(4/22)", "on_days": {"start_end_time_desc": "15:00 ~ 22:00"}}
                        ]}
                      ]
                    }
                  }
                }
                """);

        KakaoPlaceDetailData result = parser.parse(panel, PLACE_ID);

        assertThat(result.offDays()).isEmpty();
    }

    @Test
    @DisplayName("off_days_desc 값이 '휴무일'이 아니면 off_days에 포함하지 않는다")
    void parse_ignoresNonStandardOffDaysDesc() throws Exception {
        JsonNode panel = panel("""
                {
                  "summary": {
                    "category": {"name1": "음식점", "name2": "한식", "name3": "육류,고기"},
                    "name": "테스트식당",
                    "confirm_id": "16053234",
                    "address": {"road": "서울 어딘가"},
                    "point": {"lat": 37.5, "lon": 127.0}
                  },
                  "kakaomap_review": {"score_set": {"average_score": 4.0, "review_count": 10}},
                  "open_hours": {
                    "week_from_today": {
                      "week_periods": [
                        {"days": [
                          {"day_of_the_week_desc": "일(4/26)", "off_days_desc": "임시휴무"}
                        ]}
                      ]
                    }
                  }
                }
                """);

        KakaoPlaceDetailData result = parser.parse(panel, PLACE_ID);

        assertThat(result.offDays()).isEmpty();
    }

    @Test
    @DisplayName("동일한 날짜가 여러 period에 중복 등장해도 한 번만 수집한다")
    void parse_deduplicatesOffDays() throws Exception {
        int year = YEAR;
        JsonNode panel = panel("""
                {
                  "summary": {
                    "category": {"name1": "음식점", "name2": "한식", "name3": "육류,고기"},
                    "name": "테스트식당",
                    "confirm_id": "16053234",
                    "address": {"road": "서울 어딘가"},
                    "point": {"lat": 37.5, "lon": 127.0}
                  },
                  "kakaomap_review": {"score_set": {"average_score": 4.0, "review_count": 10}},
                  "open_hours": {
                    "week_from_today": {
                      "week_periods": [
                        {"days": [
                          {"day_of_the_week_desc": "일(4/26)", "off_days_desc": "휴무일"}
                        ]},
                        {"days": [
                          {"day_of_the_week_desc": "일(4/26)", "off_days_desc": "휴무일"}
                        ]}
                      ]
                    }
                  }
                }
                """);

        KakaoPlaceDetailData result = parser.parse(panel, PLACE_ID);

        assertThat(result.offDays())
                .hasSize(1)
                .containsExactly(LocalDate.of(year, 4, 26));
    }

    @Test
    @DisplayName("open_hours가 없으면 off_days는 빈 리스트다")
    void parse_emptyOffDaysWhenNoOpenHours() throws Exception {
        JsonNode panel = panel("""
                {
                  "summary": {
                    "category": {"name1": "음식점", "name2": "한식", "name3": "육류,고기"},
                    "name": "테스트식당",
                    "confirm_id": "16053234",
                    "address": {"road": "서울 어딘가"},
                    "point": {"lat": 37.5, "lon": 127.0}
                  },
                  "kakaomap_review": {"score_set": {"average_score": 4.0, "review_count": 10}}
                }
                """);

        KakaoPlaceDetailData result = parser.parse(panel, PLACE_ID);

        assertThat(result.offDays()).isEmpty();
    }

    private static JsonNode panel(String json) throws Exception {
        return OBJECT_MAPPER.readTree(json);
    }
}
