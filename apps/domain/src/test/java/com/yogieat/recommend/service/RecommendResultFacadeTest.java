package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.service.ParticipantAnalyzer;
import com.yogieat.participant.service.ParticipantService;
import com.yogieat.recommend.domain.RecommendRerollHistory;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantService;
import com.yogieat.util.LockManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecommendResultFacadeTest {

    @Mock
    private GatheringService gatheringService;

    @Mock
    private RecommendResultService recommendResultService;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ParticipantService participantService;

    @Mock
    private ParticipantAnalyzer participantAnalyzer;

    @Mock
    private LockManager lockManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RecommendValidator recommendValidator;

    @Mock
    private RecommendationProcessor recommendationProcessor;

    @Mock
    private RecommendRerollHistoryService recommendRerollHistoryService;

    @InjectMocks
    private RecommendResultFacade recommendResultFacade;

    @Test
    @DisplayName("최신 reroll history가 있으면 추천 결과 조회는 reroll 결과를 우선 반환한다")
    void getRecommendResults_ShouldReturnLatestRerollResults() {
        Gathering gathering = new Gathering(
                1L,
                "access-key",
                "모임",
                LocalDate.of(2026, 3, 20),
                null,
                Region.GANGNAM,
                4,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 35.0, RecommendStatus.COMPLETED, 1, 4.1, "original")
        );
        RecommendRerollHistory rerollHistory = new RecommendRerollHistory(
                10L,
                1L,
                List.of(101L),
                List.of(
                        RecommendRerollHistory.Result.of(1, 201L, 80.0, "reroll-1"),
                        RecommendRerollHistory.Result.of(2, 202L, 60.0, "reroll-2")
                ),
                LocalDateTime.now()
        );
        List<Restaurant> rerollRestaurants = List.of(
                restaurant(201L, 1L, "reroll-a"),
                restaurant(202L, 1L, "reroll-b")
        );
        List<Category> categories = List.of(
                new Category(1L, LargeCategory.KOREAN, "한식", LocalDateTime.now())
        );

        when(gatheringService.getGatheringByAccessKey("access-key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(recommendRerollHistoryService.findLatestByGatheringId(1L)).thenReturn(Optional.of(rerollHistory));
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(participantAnalyzer.determineMajorityDistanceRange(any(), eq(Region.GANGNAM))).thenReturn(DistanceRange.ANY);
        when(restaurantService.findByIds(List.of(201L, 202L))).thenReturn(rerollRestaurants);
        when(categoryService.findAll()).thenReturn(categories);

        RecommendResultData.Get result = recommendResultFacade.getRecommendResults("access-key");

        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::restaurantId)
                .containsExactly(201L, 202L);
        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::reasonText)
                .containsExactly("reroll-1", "reroll-2");
        assertThat(result.averageAgreementRate()).isEqualTo(70.0);
        verify(restaurantService).findByIds(List.of(201L, 202L));
    }

    private Restaurant restaurant(Long id, Long categoryId, String name) {
        return new Restaurant(
                id,
                "ext-" + id,
                categoryId,
                name,
                "address-" + id,
                4.5,
                null,
                null,
                null,
                null,
                Region.GANGNAM,
                new GeoJson.Point(List.of(127.0, 37.0)),
                10,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
