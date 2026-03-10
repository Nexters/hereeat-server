package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;
import com.yogieat.recommend.domain.value.CategoryVoteSummary;
import com.yogieat.recommend.domain.value.RecommendationParticipantContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendationContextFactoryScenarioTest {

    private final RecommendationContextFactory contextFactory = new RecommendationContextFactory();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("scenarioCases")
    @DisplayName("시나리오별 제외 카테고리를 정책대로 계산한다")
    void shouldCalculateExcludedCategoriesByPolicy(
            String scenarioName,
            List<Participant> participants,
            Set<String> expectedExcludedCategories
    ) {
        RecommendationParticipantContext context =
                contextFactory.create(participants, RecommendationScoringPolicy.defaults());
        CategoryVoteSummary voteSummary = context.categoryVoteSummary();

        assertThat(voteSummary.excludedCategories())
                .as("scenario: " + scenarioName)
                .containsExactlyInAnyOrderElementsOf(expectedExcludedCategories);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("공백이 포함된 입력도 카테고리 집계에 반영된다")
    void shouldNormalizeWhitespaceCategoryNames() {
        List<Participant> participants = List.of(
                participant(1L, "한식, 중식, 양식", "아시안"),
                participant(2L, "아시안", " 한식 ")
        );

        RecommendationParticipantContext context =
                contextFactory.create(participants, RecommendationScoringPolicy.defaults());

        assertThat(context.categoryVoteSummary().preferenceVotes())
                .containsEntry("한식", 1)
                .containsEntry("중식", 1)
                .containsEntry("양식", 1)
                .containsEntry("아시안", 1);

        assertThat(context.categoryVoteSummary().dislikeVotes())
                .containsEntry("아시안", 1)
                .containsEntry("한식", 1);
    }

    private static Stream<Arguments> scenarioCases() {
        return Stream.of(
                Arguments.of(
                        "Case 1 (중식만 생존)",
                        List.of(
                                participant(1L, "중식", "일식,양식"),
                                participant(2L, "중식", "한식,아시안"),
                                participant(3L, "중식", "일식,양식"),
                                participant(4L, "중식", "한식,아시안"),
                                participant(5L, "중식", "일식,양식"),
                                participant(6L, "중식", "한식,아시안")
                        ),
                        Set.of("한식", "일식", "양식", "아시안")
                ),
                Arguments.of(
                        "Case 2 (전원 충돌)",
                        List.of(
                                participant(1L, "중식", "한식,일식"),
                                participant(2L, "한식", "중식"),
                                participant(3L, "양식", "아시안,한식"),
                                participant(4L, "아시안", "일식,중식"),
                                participant(5L, "일식", "아시안"),
                                participant(6L, "양식", "한식,중식")
                        ),
                        Set.of("한식", "일식", "중식", "아시안")
                ),
                Arguments.of(
                        "Case 3 (상관없음)",
                        List.of(
                                participant(1L, "상관없음", "없음"),
                                participant(2L, "상관없음", "없음"),
                                participant(3L, "상관없음", "없음"),
                                participant(4L, "상관없음", "없음"),
                                participant(5L, "상관없음", "없음"),
                                participant(6L, "상관없음", "없음")
                        ),
                        Set.of()
                ),
                Arguments.of(
                        "Case 4 (강력 선호)",
                        List.of(
                                participant(1L, "한식", "없음"),
                                participant(2L, "한식", "없음"),
                                participant(3L, "한식", "없음"),
                                participant(4L, "한식", "없음"),
                                participant(5L, "한식", "없음"),
                                participant(6L, "한식", "없음")
                        ),
                        Set.of()
                ),
                Arguments.of(
                        "Case 5 (특정 제외)",
                        List.of(
                                participant(1L, "일식", "한식,중식"),
                                participant(2L, "양식", "한식,중식"),
                                participant(3L, "아시안", "한식,중식"),
                                participant(4L, "일식", "한식,중식"),
                                participant(5L, "양식", "한식,중식"),
                                participant(6L, "아시안", "한식,중식")
                        ),
                        Set.of("한식", "중식")
                ),
                Arguments.of(
                        "Case 6 (필터링)",
                        List.of(
                                participant(1L, "상관없음", "중식,아시안"),
                                participant(2L, "상관없음", "중식,아시안"),
                                participant(3L, "상관없음", "중식,아시안"),
                                participant(4L, "상관없음", "중식,아시안"),
                                participant(5L, "상관없음", "중식,아시안"),
                                participant(6L, "상관없음", "중식,아시안")
                        ),
                        Set.of("중식", "아시안")
                ),
                Arguments.of(
                        "Case 7 (전 카테고리 등장)",
                        List.of(
                                participant(1L, "양식", "한식,일식"),
                                participant(2L, "일식", "중식"),
                                participant(3L, "중식", "한식"),
                                participant(4L, "양식", "일식,중식"),
                                participant(5L, "아시안", "일식,중식"),
                                participant(6L, "상관없음", "없음")
                        ),
                        Set.of("한식", "일식", "중식")
                ),
                Arguments.of(
                        "Case 8 (클린)",
                        List.of(
                                participant(1L, "일식", "없음"),
                                participant(2L, "일식", "없음"),
                                participant(3L, "일식", "없음"),
                                participant(4L, "중식", "없음"),
                                participant(5L, "중식", "없음"),
                                participant(6L, "중식", "없음")
                        ),
                        Set.of()
                ),
                Arguments.of(
                        "Case 9 (불호 만장일치)",
                        List.of(
                                participant(1L, "아시안", "한식"),
                                participant(2L, "아시안", "한식"),
                                participant(3L, "양식,일식", "한식"),
                                participant(4L, "양식", "한식"),
                                participant(5L, "중식", "한식"),
                                participant(6L, "상관없음", "한식")
                        ),
                        Set.of("한식")
                ),
                Arguments.of(
                        "Case 10 (균등한 불호)",
                        List.of(
                                participant(1L, "중식", "일식,아시안"),
                                participant(2L, "중식", "일식,아시안"),
                                participant(3L, "중식", "양식,아시안"),
                                participant(4L, "중식", "양식,아시안"),
                                participant(5L, "중식", "한식"),
                                participant(6L, "양식", "한식")
                        ),
                        Set.of("한식", "일식", "양식", "아시안")
                ),
                Arguments.of(
                        "Case 11 (불호 데이터 최대)",
                        List.of(
                                participant(1L, "한식", "한식,양식"),
                                participant(2L, "한식", "일식,아시안"),
                                participant(3L, "양식", "중식,한식"),
                                participant(4L, "한식", "일식,양식"),
                                participant(5L, "한식", "아시안,중식"),
                                participant(6L, "양식", "한식,일식")
                        ),
                        Set.of("일식", "중식", "아시안")
                ),
                Arguments.of(
                        "Case 12 (데이터 최소)",
                        List.of(
                                participant(1L, "중식", "한식"),
                                participant(2L, "아시안", "중식"),
                                participant(3L, "한식", "양식,아시안"),
                                participant(4L, "일식", "상관없음"),
                                participant(5L, "한식", "상관없음"),
                                participant(6L, "중식", "상관없음")
                        ),
                        Set.of("양식")
                )
        );
    }

    private static Participant participant(
            Long participantId,
            String preferences,
            String dislikes
    ) {
        return new Participant(
                participantId,
                null,
                100L,
                "user-" + participantId,
                DistanceRange.ANY,
                preferences,
                dislikes,
                Role.MEMBER,
                null,
                null
        );
    }
}
