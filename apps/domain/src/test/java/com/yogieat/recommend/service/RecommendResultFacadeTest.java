package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.yogieat.recommend.domain.RecommendRerollHistory;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.result.RecommendResultData;
import com.yogieat.recommend.domain.value.CategoryAggregation;
import com.yogieat.recommend.domain.value.FailureReason;
import com.yogieat.recommend.domain.value.RecommendStatus;
import com.yogieat.recommend.domain.value.ScoredRestaurant;
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
    @DisplayName("v2 조회: 추천 결과가 없으면 status=null, 빈 랭킹을 반환한다")
    void getRecommendResultsV2_ShouldReturnEmpty_WhenNoResults() {
        Gathering gathering = gathering(1L, "key");
        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(List.of());

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isNull();
        assertThat(result.rankings()).isEmpty();
        verifyNoInteractions(recommendRerollHistoryService, lockManager);
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
        verifyNoInteractions(recommendRerollHistoryService, lockManager);
    }

    @Test
    @DisplayName("v2 조회: reroll 이력이 이미 있으면 락 없이 원본+reroll 결과를 합쳐 반환한다")
    void getRecommendResultsV2_ShouldReturnCachedReroll_WithoutLock() {
        Gathering gathering = gathering(1L, "key");
        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 80.0, RecommendStatus.COMPLETED, 1, 4.0, "원본1"),
                RecommendResult.Create.of(1L, 102L, 60.0, RecommendStatus.COMPLETED, 2, 3.8, "원본2"),
                RecommendResult.Create.of(1L, 103L, 50.0, RecommendStatus.COMPLETED, 3, 3.5, "원본3")
        );
        List<RecommendRerollHistory.Result> cachedReroll = List.of(
                RecommendRerollHistory.Result.of(1, 201L, 40.0, "reroll1"),
                RecommendRerollHistory.Result.of(2, 202L, 30.0, "reroll2")
        );
        RecommendRerollHistory history = new RecommendRerollHistory(
                10L, 1L, List.of(101L, 102L, 103L), cachedReroll, LocalDateTime.now()
        );

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(List.of(101L, 102L, 103L))).thenReturn(List.of(
                restaurant(101L, 1L, "원본식당1"),
                restaurant(102L, 1L, "원본식당2"),
                restaurant(103L, 1L, "원본식당3")
        ));
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);
        when(recommendRerollHistoryService.findLatestByGatheringId(1L)).thenReturn(Optional.of(history));
        when(restaurantService.findByIds(List.of(201L, 202L))).thenReturn(List.of(
                restaurant(201L, 1L, "reroll식당1"),
                restaurant(202L, 1L, "reroll식당2")
        ));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.COMPLETED);
        assertThat(result.rankings()).hasSize(5);
        assertThat(result.rankings()).extracting(RecommendResultData.Ranking::rank)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(result.rankings().get(3).restaurantId()).isEqualTo(201L);
        assertThat(result.rankings().get(4).restaurantId()).isEqualTo(202L);
        verify(lockManager, never()).executeWithLock(anyString(), any());
    }

    @Test
    @DisplayName("v2 조회: reroll 이력 없으면 락 내에서 계산하고 저장한 뒤 반환한다")
    @SuppressWarnings("unchecked")
    void getRecommendResultsV2_ShouldCalculateAndSaveReroll_WhenNoCachedHistory() {
        Gathering gathering = gathering(1L, "key");
        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 70.0, RecommendStatus.COMPLETED, 1, 4.0, "원본1"),
                RecommendResult.Create.of(1L, 102L, 60.0, RecommendStatus.COMPLETED, 2, 3.8, "원본2"),
                RecommendResult.Create.of(1L, 103L, 50.0, RecommendStatus.COMPLETED, 3, 3.5, "원본3")
        );
        Restaurant rerollRestaurant = restaurant(201L, 1L, "재추천식당1");
        ScoredRestaurant scoredRestaurant = new ScoredRestaurant(rerollRestaurant, 3.5, 40.0, "재추천근거");

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(List.of(101L, 102L, 103L))).thenReturn(List.of(
                restaurant(101L, 1L, "원본식당1"),
                restaurant(102L, 1L, "원본식당2"),
                restaurant(103L, 1L, "원본식당3")
        ));
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);
        // 1차 조회: 이력 없음, 2차 조회(락 내부): 이력 없음
        when(recommendRerollHistoryService.findLatestByGatheringId(1L)).thenReturn(Optional.empty());
        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            LockManager.Task<List<RecommendRerollHistory.Result>> task = invocation.getArgument(1);
            return task.execute();
        });
        when(recommendationProcessor.calculateRecommendations(
                eq(1L), any(), eq(List.of(101L, 102L, 103L)), eq(6)
        )).thenReturn(RecommendationCandidateResult.success(List.of(scoredRestaurant)));
        when(restaurantService.findByIds(List.of(201L))).thenReturn(List.of(rerollRestaurant));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.COMPLETED);
        assertThat(result.rankings()).hasSize(4);
        assertThat(result.rankings().get(3).restaurantId()).isEqualTo(201L);
        assertThat(result.rankings().get(3).reasonText()).isEqualTo("재추천근거");
        verify(recommendRerollHistoryService).create(eq(1L), eq(List.of(101L, 102L, 103L)), anyList());
    }

    @Test
    @DisplayName("v2 조회: 락 내부에서 이력이 새로 생기면 재계산 없이 내부 이력을 반환한다 (Double-Check)")
    @SuppressWarnings("unchecked")
    void getRecommendResultsV2_ShouldUseInnerHistory_WhenHistoryAppearsInsideLock() {
        Gathering gathering = gathering(1L, "key");
        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 70.0, RecommendStatus.COMPLETED, 1, 4.0, "원본1")
        );
        List<RecommendRerollHistory.Result> innerReroll = List.of(
                RecommendRerollHistory.Result.of(1, 301L, 35.0, "내부이력reroll")
        );
        RecommendRerollHistory innerHistory = new RecommendRerollHistory(
                20L, 1L, List.of(101L), innerReroll, LocalDateTime.now()
        );

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(List.of(101L))).thenReturn(List.of(restaurant(101L, 1L, "원본식당1")));
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);
        // 1차 조회: 이력 없음, 2차 조회(락 내부): 이력 있음
        when(recommendRerollHistoryService.findLatestByGatheringId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(innerHistory));
        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            LockManager.Task<List<RecommendRerollHistory.Result>> task = invocation.getArgument(1);
            return task.execute();
        });
        when(restaurantService.findByIds(List.of(301L))).thenReturn(List.of(restaurant(301L, 1L, "내부이력식당")));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.rankings()).hasSize(2);
        assertThat(result.rankings().get(1).restaurantId()).isEqualTo(301L);
        verify(recommendationProcessor, never()).calculateRecommendations(any(), any(), any(), any(Integer.class));
        verify(recommendRerollHistoryService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("v2 조회: reroll 계산이 실패하면 원본 결과만 반환한다")
    @SuppressWarnings("unchecked")
    void getRecommendResultsV2_ShouldReturnOnlyOriginal_WhenRerollCalculationFails() {
        Gathering gathering = gathering(1L, "key");
        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 70.0, RecommendStatus.COMPLETED, 1, 4.0, "원본1"),
                RecommendResult.Create.of(1L, 102L, 60.0, RecommendStatus.COMPLETED, 2, 3.8, "원본2"),
                RecommendResult.Create.of(1L, 103L, 50.0, RecommendStatus.COMPLETED, 3, 3.5, "원본3")
        );

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(List.of(101L, 102L, 103L))).thenReturn(List.of(
                restaurant(101L, 1L, "원본식당1"),
                restaurant(102L, 1L, "원본식당2"),
                restaurant(103L, 1L, "원본식당3")
        ));
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);
        when(recommendRerollHistoryService.findLatestByGatheringId(1L)).thenReturn(Optional.empty());
        when(lockManager.executeWithLock(anyString(), any())).thenAnswer(invocation -> {
            LockManager.Task<List<RecommendRerollHistory.Result>> task = invocation.getArgument(1);
            return task.execute();
        });
        when(recommendationProcessor.calculateRecommendations(any(), any(), any(), any(Integer.class)))
                .thenReturn(RecommendationCandidateResult.failure(FailureReason.NO_RESTAURANTS, "후보 없음"));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.status()).isEqualTo(RecommendStatus.COMPLETED);
        assertThat(result.rankings()).hasSize(3);
        assertThat(result.rankings()).extracting(RecommendResultData.Ranking::restaurantId)
                .containsExactly(101L, 102L, 103L);
        verify(recommendRerollHistoryService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("v2 조회: reroll 음식점이 DB에 없으면 해당 항목을 건너뛴다")
    @SuppressWarnings("unchecked")
    void getRecommendResultsV2_ShouldSkipMissingRerollRestaurant_WhenNotFoundInDb() {
        Gathering gathering = gathering(1L, "key");
        List<RecommendResult> originalResults = List.of(
                RecommendResult.Create.of(1L, 101L, 70.0, RecommendStatus.COMPLETED, 1, 4.0, "원본1")
        );
        List<RecommendRerollHistory.Result> cachedReroll = List.of(
                RecommendRerollHistory.Result.of(1, 201L, 40.0, "존재하는reroll"),
                RecommendRerollHistory.Result.of(2, 999L, 10.0, "없는음식점reroll")
        );
        RecommendRerollHistory history = new RecommendRerollHistory(
                10L, 1L, List.of(101L), cachedReroll, LocalDateTime.now()
        );

        when(gatheringService.getGatheringByAccessKey("key")).thenReturn(gathering);
        when(recommendResultService.findByGatheringId(1L)).thenReturn(originalResults);
        when(participantService.getByGatheringId(1L)).thenReturn(List.of());
        when(participantAnalyzer.aggregateCategoryPreferences(List.of())).thenReturn(CategoryAggregation.of(Map.of(), Map.of()));
        when(participantAnalyzer.aggregateDistanceRanges(List.of())).thenReturn(Map.of());
        when(restaurantService.findByIds(List.of(101L))).thenReturn(List.of(restaurant(101L, 1L, "원본식당1")));
        when(categoryService.findAll()).thenReturn(List.of(category(1L)));
        when(participantAnalyzer.determineMajorityDistanceRange(any(), any())).thenReturn(DistanceRange.ANY);
        when(recommendRerollHistoryService.findLatestByGatheringId(1L)).thenReturn(Optional.of(history));
        // 201L만 DB에 존재, 999L은 없음
        when(restaurantService.findByIds(List.of(201L, 999L))).thenReturn(List.of(restaurant(201L, 1L, "reroll식당")));

        RecommendResultData.Get result = recommendResultFacade.getRecommendResultsV2("key");

        assertThat(result.rankings()).hasSize(2);
        assertThat(result.rankings().get(1).restaurantId()).isEqualTo(201L);
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
