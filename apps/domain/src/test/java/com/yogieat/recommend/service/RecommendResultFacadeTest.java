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
import java.util.ArrayList;
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
                Region.fromString("GANGNAM"),
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
        when(participantAnalyzer.determineMajorityDistanceRange(any(), eq(Region.fromString("GANGNAM")))).thenReturn(DistanceRange.ANY);
        when(restaurantService.findByIds(List.of(101L))).thenReturn(originalRestaurants);
        when(categoryService.findAll()).thenReturn(categories);

        RecommendResultData.Get result = recommendResultFacade.getRecommendResults("access-key");

        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::restaurantId)
                .containsExactly(101L);
        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::reasonText)
                .containsExactly("original");
        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::teamRecommendationTitle)
                .containsExactly("요기잇 개발자 픽");
        assertThat(result.rankings())
                .extracting(RecommendResultData.Ranking::teamRecommendationReason)
                .containsExactly("여기 정말 가봤는데, 메뉴가 맛있어요");
        assertThat(result.averageAgreementRate()).isEqualTo(35.0);
        verify(restaurantService).findByIds(List.of(101L));
        verifyNoInteractions(recommendRerollHistoryService);
    }

    @Test
    @DisplayName("FAILED 상태이면 status=FAILED, 빈 랭킹을 반환한다 (restaurantId=null로 인한 예외 방지)")
    void getRecommendResults_ShouldReturnFailed_WhenFirstResultIsFailed() {
        Gathering gathering = gathering(1L, "access-key");
        when(gatheringService.getGatheringByAccessKey("access-key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(List.of(
                RecommendResult.Create.of(1L, null, 0.0, RecommendStatus.FAILED, null, 0.0, null)
        ));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResults("access-key");

        assertThat(result.status()).isEqualTo(RecommendStatus.FAILED);
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(restaurantService, participantService);
    }

    @Test
    @DisplayName("v2 조회: FAILED 상태이면 status=FAILED, 빈 랭킹을 반환한다 (restaurantId=null로 인한 예외 방지)")
    void getRecommendResultsV2_ShouldReturnFailed_WhenFirstResultIsFailed() {
        Gathering gathering = gathering(1L, "key");
        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(List.of(
                RecommendResult.Create.of(1L, null, 0.0, RecommendStatus.FAILED, null, 0.0, null)
        ));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.FAILED);
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(restaurantService, participantService, lockManager);
    }

    @Test
    @DisplayName("v2 조회: 추천 결과가 없으면 status=null, 빈 랭킹을 반환한다")
    void getRecommendResultsV2_ShouldReturnEmpty_WhenNoResults() {
        Gathering gathering = gathering(1L, "key");
        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(List.of());

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isNull();
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(recommendRerollHistoryService, lockManager, recommendationProcessor);
    }

    @Test
    @DisplayName("v2 조회: 첫 번째 결과가 PENDING이면 status=PENDING, 빈 랭킹을 반환한다")
    void getRecommendResultsV2_ShouldReturnPending_WhenFirstResultIsPending() {
        Gathering gathering = gathering(1L, "key");
        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(List.of(
                RecommendResult.Create.of(1L, 101L, 0.0, RecommendStatus.PENDING, 1, 0.0, null)
        ));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.PENDING);
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(recommendRerollHistoryService, lockManager, recommendationProcessor);
    }

    @Test
    @DisplayName("v2 조회: 생성 시점에 적재된 추천 결과를 락/리롤 계산 없이 rank 순서대로 그대로 반환한다")
    void getRecommendResultsV2_ShouldReturnAllStoredResults() {
        Gathering gathering = gathering(1L, "key");

        List<RecommendResult> storedResults = new ArrayList<>();
        List<Restaurant> storedRestaurants = new ArrayList<>();
        List<Long> restaurantIds = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++) {
            long restaurantId = 100L + rank;
            storedResults.add(RecommendResult.Create.of(
                    1L, restaurantId, 50.0, RecommendStatus.COMPLETED, rank, 4.0, "근거" + rank));
            storedRestaurants.add(restaurant(restaurantId, 1L, "식당" + rank));
            restaurantIds.add(restaurantId);
        }

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(storedResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(restaurantIds)).thenReturn(storedRestaurants);
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.COMPLETED);
        assertThat(result.rankings()).hasSize(9);
        assertThat(result.rankings()).extracting(RecommendResultData.Ranking::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(result.rankings()).extracting(RecommendResultData.Ranking::restaurantId)
                .containsExactly(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);
        // 조회 경로에서 reroll 계산/락을 더 이상 사용하지 않는다
        verify(restaurantService).findByIds(restaurantIds);
        verifyNoInteractions(lockManager, recommendRerollHistoryService, recommendationProcessor);
    }

    private Gathering gathering(Long id, String accessKey) {
        return new Gathering(
                id,
                accessKey,
                "모임",
                LocalDate.of(2026, 3, 20),
                null,
                Region.fromString("GANGNAM"),
                4,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private Category category(Long id) {
        return new Category(id, LargeCategory.KOREAN, "한식", LocalDateTime.now());
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
                Region.fromString("GANGNAM"),
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
                null,
                null,
                "요기잇 개발자 픽",
                "여기 정말 가봤는데, 메뉴가 맛있어요",
                true
        );
    }
}
