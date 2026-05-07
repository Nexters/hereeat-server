package com.yogieat.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.service.GatheringRepository;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;
import com.yogieat.participant.service.ParticipantRepository;
import com.yogieat.recommend.domain.RecommendResult;
import com.yogieat.recommend.domain.RecommendResultFailed;
import com.yogieat.recommend.service.strategy.CategoryQuotaSelectionStrategy;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationProcessorTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private RecommendResultRepository recommendResultRepository;

    @Mock
    private RecommendResultFailedRepository recommendResultFailedRepository;

    @Mock
    private GatheringRepository gatheringRepository;

    private RecommendationProcessor recommendationProcessor;
    private List<List<RecommendResult>> savedRecommendationBatches;
    private Collection<Long> capturedCandidateCategoryIds;
    private TimeSlot capturedCandidateTimeSlot;
    private Collection<Long> capturedExcludedRestaurantIds;

    @BeforeEach
    void setUp() {
        savedRecommendationBatches = new ArrayList<>();
        capturedCandidateCategoryIds = List.of();
        capturedCandidateTimeSlot = null;
        capturedExcludedRestaurantIds = List.of();

        lenient().when(recommendResultRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<RecommendResult> saved = List.copyOf(invocation.getArgument(0));
            savedRecommendationBatches.add(saved);
            return saved;
        });
        lenient().when(recommendResultFailedRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0, RecommendResultFailed.class)
        );

        recommendationProcessor = new RecommendationProcessor(
                RecommendationScoringPolicy.defaults(),
                participantRepository,
                restaurantRepository,
                categoryService,
                recommendResultRepository,
                recommendResultFailedRepository,
                gatheringRepository,
                new RecommendationContextFactory(),
                new CategoryQuotaSelectionStrategy()
        );
    }

    @Test
    @DisplayName("선호표 3:2는 Top3 슬롯을 2:1로 배분한다")
    void returnsTwoJapaneseAndOneAsian_when_preferenceVotesAreThreeToTwo() {
        Long gatheringId = 1L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "일식", null),
                participant(2L, gatheringId, DistanceRange.ANY, "일식", null),
                participant(3L, gatheringId, DistanceRange.ANY, "일식", null),
                participant(4L, gatheringId, DistanceRange.ANY, "아시안", null),
                participant(5L, gatheringId, DistanceRange.ANY, "아시안", null)
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.JAPANESE),
                category(2L, LargeCategory.ASIAN),
                category(3L, LargeCategory.KOREAN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "일식A", 4.8, point(127.0276, 37.4979), 20),
                restaurant(102L, 1L, "일식B", 4.5, point(127.0277, 37.4978), 20),
                restaurant(201L, 2L, "아시안A", 4.9, point(127.0278, 37.4977), 20),
                restaurant(301L, 3L, "한식A", 5.0, point(127.0279, 37.4976), 20)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(101L, 102L, 201L);
    }

    @Test
    @DisplayName("불호가 선호보다 많은 카테고리는 추천 대상에서 제외된다")
    void excludesCategoryFromRecommendations_when_dislikeVotesExceedPreferenceVotes() {
        Long gatheringId = 2L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식", "일식,아시안"),
                participant(2L, gatheringId, DistanceRange.ANY, "한식", "일식,아시안"),
                participant(3L, gatheringId, DistanceRange.ANY, "한식", "일식,아시안"),
                participant(4L, gatheringId, DistanceRange.ANY, "일식", "한식"),
                participant(5L, gatheringId, DistanceRange.ANY, "일식", "중식"),
                participant(6L, gatheringId, DistanceRange.ANY, "아시안", "양식"),
                participant(7L, gatheringId, DistanceRange.ANY, "아시안", null)
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.JAPANESE),
                category(3L, LargeCategory.ASIAN),
                category(4L, LargeCategory.CHINESE),
                category(5L, LargeCategory.WESTERN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(301L, 1L, "한식A", 4.9, point(127.0276, 37.4979), 30),
                restaurant(302L, 1L, "한식B", 4.7, point(127.0277, 37.4978), 25),
                restaurant(303L, 1L, "한식C", 4.6, point(127.0278, 37.4977), 20),
                restaurant(101L, 2L, "일식A", 5.0, point(127.0279, 37.4976), 30),
                restaurant(201L, 3L, "아시안A", 5.0, point(127.0280, 37.4975), 30),
                restaurant(401L, 4L, "중식A", 5.0, point(127.0281, 37.4974), 30),
                restaurant(501L, 5L, "양식A", 5.0, point(127.0282, 37.4973), 30)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);

        List<Long> idsByRank = restaurantIdsByRank(savedRecommendationBatches.get(0));
        assertThat(idsByRank.getFirst()).isIn(301L, 302L, 303L);
        assertThat(idsByRank).doesNotContainAnyElementsOf(Set.of(101L, 201L, 401L, 501L));
    }

    @Test
    @DisplayName("ANY 비중이 높을수록 거리 보너스가 선형 축소된다")
    void reducesDistanceBonusLinearly_when_anyDistanceRatioIncreases() {
        Region region = Region.GANGNAM;
        List<Category> categories = List.of(category(1L, LargeCategory.KOREAN));
        List<Restaurant> restaurants = List.of(
                restaurant(1001L, 1L, "원거리한식", 4.5, point(127.0400, 37.5100), 10),
                restaurant(1002L, 1L, "근거리한식", 4.5, point(127.0276, 37.4979), 10)
        );

        when(recommendResultRepository.findByGatheringId(11L)).thenReturn(List.of());
        when(recommendResultRepository.findByGatheringId(12L)).thenReturn(List.of());
        when(recommendResultRepository.findByGatheringId(13L)).thenReturn(List.of());
        when(gatheringRepository.findById(11L)).thenReturn(Optional.empty());
        when(gatheringRepository.findById(12L)).thenReturn(Optional.empty());
        when(gatheringRepository.findById(13L)).thenReturn(Optional.empty());
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        when(participantRepository.findByGatheringId(11L)).thenReturn(List.of(
                participant(1L, 11L, DistanceRange.RANGE_500M, "한식", null),
                participant(2L, 11L, DistanceRange.RANGE_500M, "한식", null)
        ));
        when(participantRepository.findByGatheringId(12L)).thenReturn(List.of(
                participant(1L, 12L, DistanceRange.RANGE_500M, "한식", null),
                participant(2L, 12L, DistanceRange.ANY, "한식", null)
        ));
        when(participantRepository.findByGatheringId(13L)).thenReturn(List.of(
                participant(1L, 13L, DistanceRange.ANY, "한식", null),
                participant(2L, 13L, DistanceRange.ANY, "한식", null)
        ));

        recommendationProcessor.processRecommendation(11L, region);
        recommendationProcessor.processRecommendation(12L, region);
        recommendationProcessor.processRecommendation(13L, region);

        assertThat(savedRecommendationBatches).hasSize(3);

        RecommendResult topAll500m = findRank(savedRecommendationBatches.get(0), 1);
        RecommendResult topHalfAny = findRank(savedRecommendationBatches.get(1), 1);
        RecommendResult topAllAny = findRank(savedRecommendationBatches.get(2), 1);

        assertThat(topAll500m.score() - topHalfAny.score()).isCloseTo(0.5, within(0.001));
        assertThat(topHalfAny.score() - topAllAny.score()).isCloseTo(0.5, within(0.001));
        assertThat(topAllAny.restaurantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("추천 후보 조회 시 불호 우세 카테고리를 제외하고 TimeSlot을 전달한다")
    void passesFilteredCategoriesAndGatheringTimeSlot_when_loadingRecommendationCandidates() {
        Long gatheringId = 21L;
        Region region = Region.GANGNAM;
        Gathering gathering = new Gathering(
                gatheringId,
                "access-key",
                "점심 모임",
                null,
                TimeSlot.LUNCH,
                region,
                4,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식", "일식"),
                participant(2L, gatheringId, DistanceRange.ANY, "한식", "일식"),
                participant(3L, gatheringId, DistanceRange.ANY, "일식", null)
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.JAPANESE),
                category(3L, LargeCategory.ASIAN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(301L, 1L, "한식A", 4.8, point(127.0276, 37.4979), 20),
                restaurant(302L, 1L, "한식B", 4.6, point(127.0277, 37.4978), 12)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.of(gathering));
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(categoryService.findAll()).thenReturn(categories);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any())).thenAnswer(invocation -> {
            Collection<Long> categoryIds = invocation.getArgument(1);
            capturedCandidateCategoryIds = List.copyOf(categoryIds);
            capturedCandidateTimeSlot = invocation.getArgument(2, TimeSlot.class);
            return restaurants;
        });

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(capturedCandidateCategoryIds).containsExactlyInAnyOrder(1L, 3L);
        assertThat(capturedCandidateCategoryIds).doesNotContain(2L);
        assertThat(capturedCandidateTimeSlot).isEqualTo(TimeSlot.LUNCH);
    }

    @Test
    @DisplayName("재추천 계산 시 제외한 맛집 ID는 후보 조회 단계에서 제외한다")
    void returnsOnlyNonExcludedRestaurants_when_calculatingRerollRecommendations() {
        Long gatheringId = 22L;
        Region region = Region.GANGNAM;
        Gathering gathering = new Gathering(
                gatheringId,
                "access-key",
                "저녁 모임",
                null,
                TimeSlot.DINNER,
                region,
                4,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식", null),
                participant(2L, gatheringId, DistanceRange.ANY, "한식", null),
                participant(3L, gatheringId, DistanceRange.ANY, "한식", null),
                participant(4L, gatheringId, DistanceRange.ANY, "한식", null)
        );

        List<Category> categories = List.of(category(1L, LargeCategory.KOREAN));

        List<Long> excludedRestaurantIds = List.of(101L, 102L);
        List<Restaurant> rerollCandidates = List.of(
                restaurant(103L, 1L, "한식C", 4.7, point(127.0276, 37.4979), 20),
                restaurant(104L, 1L, "한식D", 4.6, point(127.0277, 37.4978), 18),
                restaurant(105L, 1L, "한식E", 4.5, point(127.0278, 37.4977), 16)
        );

        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.of(gathering));
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(categoryService.findAll()).thenReturn(categories);
        when(restaurantRepository.findRecommendationCandidates(
                eq(region),
                anyCollection(),
                eq(TimeSlot.DINNER),
                eq(excludedRestaurantIds),
                any()
        )).thenAnswer(invocation -> {
            Collection<Long> categoryIds = invocation.getArgument(1);
            Collection<Long> excludedIds = invocation.getArgument(3);
            capturedCandidateCategoryIds = List.copyOf(categoryIds);
            capturedCandidateTimeSlot = invocation.getArgument(2, TimeSlot.class);
            capturedExcludedRestaurantIds = List.copyOf(excludedIds);
            return rerollCandidates;
        });

        RecommendationCandidateResult result = recommendationProcessor.calculateRecommendations(
                gatheringId,
                region,
                excludedRestaurantIds
        );

        assertThat(result.failed()).isFalse();
        assertThat(result.restaurants())
                .extracting(scored -> scored.restaurant().id())
                .containsExactly(103L, 104L, 105L);
        assertThat(capturedCandidateCategoryIds).containsExactly(1L);
        assertThat(capturedCandidateTimeSlot).isEqualTo(TimeSlot.DINNER);
        assertThat(capturedExcludedRestaurantIds).containsExactlyElementsOf(excludedRestaurantIds);
    }

    @Test
    @DisplayName("불호 0표 선호 카테고리 후보가 Top3 이상이면 해당 카테고리만 추천한다")
    void returnsOnlyStrictCategoryRestaurants_when_strictCandidatesFillTop3() {
        Long gatheringId = 31L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "중식", "한식"),
                participant(2L, gatheringId, DistanceRange.ANY, "아시안", "중식"),
                participant(3L, gatheringId, DistanceRange.ANY, "한식", "양식,아시안"),
                participant(4L, gatheringId, DistanceRange.ANY, "일식", "상관없음"),
                participant(5L, gatheringId, DistanceRange.ANY, "한식", "상관없음"),
                participant(6L, gatheringId, DistanceRange.ANY, "중식", "상관없음")
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.CHINESE),
                category(3L, LargeCategory.JAPANESE),
                category(4L, LargeCategory.WESTERN),
                category(5L, LargeCategory.ASIAN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "한식A", 4.7, point(127.0276, 37.4979), 20),
                restaurant(201L, 2L, "중식A", 4.7, point(127.0277, 37.4978), 20),
                restaurant(301L, 3L, "일식A", 4.7, point(127.0278, 37.4977), 20),
                restaurant(302L, 3L, "일식B", 4.6, point(127.02785, 37.49775), 18),
                restaurant(303L, 3L, "일식C", 4.5, point(127.0279, 37.4977), 16),
                restaurant(401L, 4L, "양식A", 4.7, point(127.0279, 37.4976), 20),
                restaurant(501L, 5L, "아시안A", 4.7, point(127.0280, 37.4975), 20)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(301L, 302L, 303L);
    }

    @Test
    @DisplayName("Case 11: 한식 4표/양식 2표일 때 Top3를 한식 2개 + 양식 1개로 배분한다")
    void returnsTwoKoreanAndOneWestern_when_case11PreferenceVotesApply() {
        Long gatheringId = 41L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식", "양식,아시안"),
                participant(2L, gatheringId, DistanceRange.ANY, "한식", "일식,아시안"),
                participant(3L, gatheringId, DistanceRange.ANY, "양식", "중식,한식"),
                participant(4L, gatheringId, DistanceRange.ANY, "한식", "일식,양식"),
                participant(5L, gatheringId, DistanceRange.ANY, "한식", "아시안,중식"),
                participant(6L, gatheringId, DistanceRange.ANY, "양식", "한식,일식")
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.WESTERN),
                category(3L, LargeCategory.JAPANESE),
                category(4L, LargeCategory.CHINESE),
                category(5L, LargeCategory.ASIAN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "한식A", 4.9, point(127.0276, 37.4979), 30),
                restaurant(102L, 1L, "한식B", 4.7, point(127.0277, 37.4978), 25),
                restaurant(201L, 2L, "양식A", 4.8, point(127.0278, 37.4977), 25),
                restaurant(301L, 3L, "일식A", 5.0, point(127.0279, 37.4976), 30),
                restaurant(401L, 4L, "중식A", 5.0, point(127.0280, 37.4975), 30),
                restaurant(501L, 5L, "아시안A", 5.0, point(127.0281, 37.4974), 30)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(101L, 102L, 201L);
    }

    @Test
    @DisplayName("선호 입력이 모두 중립값이어도 후보 카테고리에서 Top3를 반환한다")
    void returnsTop3FromCandidateCategories_when_allPreferencesAreNeutral() {
        Long gatheringId = 51L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "상관없음", "없음"),
                participant(2L, gatheringId, DistanceRange.ANY, "상관없음", "없음"),
                participant(3L, gatheringId, DistanceRange.ANY, "상관없음", "없음"),
                participant(4L, gatheringId, DistanceRange.ANY, "상관없음", "없음"),
                participant(5L, gatheringId, DistanceRange.ANY, "상관없음", "없음"),
                participant(6L, gatheringId, DistanceRange.ANY, "상관없음", "없음")
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.JAPANESE),
                category(2L, LargeCategory.KOREAN),
                category(3L, LargeCategory.ASIAN)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "일식A", 4.8, point(127.0276, 37.4979), 30),
                restaurant(201L, 2L, "한식A", 4.5, point(127.0277, 37.4978), 20),
                restaurant(301L, 3L, "아시안A", 4.3, point(127.0278, 37.4977), 15)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(101L, 201L, 301L);
    }

    @Test
    @DisplayName("동률 선호표에서는 불호 패널티를 반영한 가중 선호점수로 카테고리 우선순위를 결정한다")
    void prioritizesWeightedPreferredCategory_when_preferenceVotesAreTied() {
        Long gatheringId = 52L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식,일식", null),
                participant(2L, gatheringId, DistanceRange.ANY, "한식", "한식"),
                participant(3L, gatheringId, DistanceRange.ANY, "일식,한식", "일식"),
                participant(4L, gatheringId, DistanceRange.ANY, "일식", "일식")
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.JAPANESE)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "한식A", 4.3, point(127.0276, 37.4979), 15),
                restaurant(102L, 1L, "한식B", 4.2, point(127.0277, 37.4978), 14),
                restaurant(201L, 2L, "일식A", 4.9, point(127.0278, 37.4977), 35),
                restaurant(202L, 2L, "일식B", 4.8, point(127.0279, 37.4976), 30)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(101L, 102L, 201L);
    }

    @Test
    @DisplayName("strict 후보가 2개뿐이면 전체 후보에서 보강해 Top3를 채운다")
    void backfillsTop3FromRemainingCandidates_when_strictCandidatesAreInsufficient() {
        Long gatheringId = 53L;
        Region region = Region.GANGNAM;

        List<Participant> participants = List.of(
                participant(1L, gatheringId, DistanceRange.ANY, "한식,일식", "없음"),
                participant(2L, gatheringId, DistanceRange.ANY, "한식,일식", "없음"),
                participant(3L, gatheringId, DistanceRange.ANY, "상관없음", "일식"),
                participant(4L, gatheringId, DistanceRange.ANY, "상관없음", "일식")
        );

        List<Category> categories = List.of(
                category(1L, LargeCategory.KOREAN),
                category(2L, LargeCategory.JAPANESE)
        );

        List<Restaurant> restaurants = List.of(
                restaurant(101L, 1L, "한식A", 4.4, point(127.0276, 37.4979), 20),
                restaurant(102L, 1L, "한식B", 4.3, point(127.0277, 37.4978), 18),
                restaurant(201L, 2L, "일식A", 4.9, point(127.0278, 37.4977), 40),
                restaurant(202L, 2L, "일식B", 4.8, point(127.0279, 37.4976), 35)
        );

        when(recommendResultRepository.findByGatheringId(gatheringId)).thenReturn(List.of());
        when(gatheringRepository.findById(gatheringId)).thenReturn(Optional.empty());
        when(participantRepository.findByGatheringId(gatheringId)).thenReturn(participants);
        when(restaurantRepository.findRecommendationCandidates(eq(region), anyCollection(), any(), anyCollection(), any()))
                .thenReturn(restaurants);
        when(categoryService.findAll()).thenReturn(categories);

        recommendationProcessor.processRecommendation(gatheringId, region);

        assertThat(savedRecommendationBatches).hasSize(1);
        assertThat(restaurantIdsByRank(savedRecommendationBatches.get(0))).containsExactly(101L, 102L, 201L);
    }

    private RecommendResult findRank(List<RecommendResult> results, int rank) {
        return results.stream()
                .filter(result -> result.rank() == rank)
                .findFirst()
                .orElseThrow();
    }

    private List<Long> restaurantIdsByRank(List<RecommendResult> results) {
        return results.stream()
                .sorted(Comparator.comparingInt(RecommendResult::rank))
                .map(RecommendResult::restaurantId)
                .toList();
    }

    private Participant participant(
            Long participantId,
            Long gatheringId,
            DistanceRange distanceRange,
            String preferences,
            String dislikes
    ) {
        return new Participant(
                participantId,
                null,
                gatheringId,
                "user-" + participantId,
                distanceRange,
                preferences,
                dislikes,
                Role.MEMBER,
                null,
                null
        );
    }

    private Category category(Long id, LargeCategory largeCategory) {
        return new Category(id, largeCategory, largeCategory.getDisplayName(), LocalDateTime.now());
    }

    private Restaurant restaurant(
            Long id,
            Long categoryId,
            String name,
            Double rating,
            GeoJson.Point location,
            Integer reviewCount
    ) {
        return new Restaurant(
                id,
                "ext-" + id,
                categoryId,
                name,
                "address-" + id,
                rating,
                null,
                null,
                null,
                null,
                Region.GANGNAM,
                location,
                reviewCount,
                0,
                null,
                null,
                null,
                null,
                null,
                TimeSlot.BOTH,
                null,
                null,
                null,
                null
        );
    }

    private GeoJson.Point point(double x, double y) {
        return new GeoJson.Point(List.of(x, y));
    }
}
