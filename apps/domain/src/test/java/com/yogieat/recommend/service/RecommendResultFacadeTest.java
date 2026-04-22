package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @DisplayName("추천 결과 조회는 reroll 이력과 무관하게 초기 추천 결과를 반환한다")
    void getRecommendResults_ShouldReturnOriginalResults() {
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
        List<Restaurant> originalRestaurants = List.of(
                restaurant(101L, 1L, "original-a")
        );
        List<Category> categories = List.of(
                new Category(1L, LargeCategory.KOREAN, "한식", LocalDateTime.now())
        );

        when(gatheringService.getGatheringByAccessKey("access-key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(participantAnalyzer.determineMajorityDistanceRange(any(), eq(Region.GANGNAM))).thenReturn(DistanceRange.ANY);
        when(restaurantService.findByIds(List.of(101L))).thenReturn(originalRestaurants);
        when(categoryService.findAll()).thenReturn(categories);

        RecommendResultData.Get result = recommendResultFacade.getRecommendResults("access-key");

        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::restaurantId)
                .containsExactly(101L);
        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::reasonText)
                .containsExactly("original");
        assertThat(result.averageAgreementRate()).isEqualTo(35.0);
        verify(restaurantService).findByIds(List.of(101L));
        verifyNoInteractions(recommendRerollHistoryService);
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
                null,
                null
        );
    }
}
