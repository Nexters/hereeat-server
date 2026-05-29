package com.yogieat.restaurant.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.service.CategoryService;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.KakaoPlaceMapper;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncChunkResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncFieldUpdatePolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RestaurantSyncServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private KakaoPlaceClient kakaoPlaceClient;

    @Mock
    private KakaoPlaceDetailClient kakaoPlaceDetailClient;

    @Mock
    private KakaoPlaceMapper kakaoPlaceMapper;

    private RestaurantSyncService restaurantSyncService;

    @Captor
    private ArgumentCaptor<List<RestaurantSyncPatchCommand>> patchCommandsCaptor;

    @BeforeEach
    void setUp() {
        restaurantSyncService = new RestaurantSyncService(
                new RestaurantSyncChunkPersistenceService(restaurantRepository),
                restaurantRepository,
                categoryService,
                kakaoPlaceClient,
                kakaoPlaceDetailClient,
                kakaoPlaceMapper
        );
    }

    @Test
    void syncChunk_success_callsBatchApplyOnce() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCoordinate("맛집", 37.498, 127.0285)));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchApplySyncPatch(anyList());
    }

    @Test
    void syncChunk_whenOnlyDetailAvailable_fillsMapUrlAndLocation() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCoordinate("맛집", 37.498, 127.0285)));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.successCount()).isEqualTo(1);
        verify(restaurantRepository).batchApplySyncPatch(patchCommandsCaptor.capture());
        RestaurantSyncPatchCommand command = patchCommandsCaptor.getValue().getFirst();
        assertThat(command.mapUrl()).isEqualTo("https://place.map.kakao.com/123");
        assertThat(command.longitude()).isEqualTo(127.0285);
        assertThat(command.latitude()).isEqualTo(37.498);
    }

    @Test
    void syncChunk_whenDetailContainsRepresentativeReview_updatesRepresentativeReview() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCoordinate("맛집", 37.498, 127.0285)));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.successCount()).isEqualTo(1);
        verify(restaurantRepository).batchApplySyncPatch(patchCommandsCaptor.capture());
        RestaurantSyncPatchCommand command = patchCommandsCaptor.getValue().getFirst();
        assertThat(command.representativeReview()).isEqualTo("리뷰");
    }

    @Test
    void syncChunk_whenKakaoCategoryIsInferable_updatesCategoryId() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "와인코르크", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCategory("술집", "와인바")));
        when(categoryService.findOrCreateCategory(eq(LargeCategory.WESTERN), eq("와인바"))).thenReturn(88L);

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.successCount()).isEqualTo(1);
        verify(restaurantRepository).batchApplySyncPatch(patchCommandsCaptor.capture());
        RestaurantSyncPatchCommand command = patchCommandsCaptor.getValue().getFirst();
        assertThat(command.categoryId()).isEqualTo(88L);
    }

    @Test
    void syncChunk_whenPreservingAdminEditableFields_omitsAdminEditableFieldsFromPatch() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "와인코르크", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCategory("술집", "와인바")));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(
                List.of(1L),
                Runnable::run,
                1,
                RestaurantSyncFieldUpdatePolicy.PRESERVE_ADMIN_EDITABLE
        );

        assertThat(result.successCount()).isEqualTo(1);
        verify(restaurantRepository).batchApplySyncPatch(patchCommandsCaptor.capture());
        RestaurantSyncPatchCommand command = patchCommandsCaptor.getValue().getFirst();
        assertThat(command.imageUrl()).isNull();
        assertThat(command.aiMateSummaryTitle()).isNull();
        assertThat(command.aiMateSummaryContents()).isNull();
        assertThat(command.categoryId()).isNull();
        assertThat(command.representativeReview()).isEqualTo("리뷰");
        verifyNoInteractions(categoryService);
    }

    @Test
    void syncChunk_whenRetryJitterRateIsZero_retriesWithoutRandomBoundError() {
        ReflectionTestUtils.setField(restaurantSyncService, "kakaoSyncRetryJitterRate", 0.0d);

        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCoordinate("맛집", 37.498, 127.0285)));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run, 1);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchApplySyncPatch(anyList());
    }

    @Test
    void syncChunk_whenRegionNull_deletesRestaurant() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", null, "123");
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchDeleteByIds(List.of(1L));
        verify(restaurantRepository, never()).batchApplySyncPatch(anyList());
        verifyNoInteractions(kakaoPlaceClient, kakaoPlaceDetailClient, kakaoPlaceMapper);
    }

    @Test
    void syncChunk_whenTargetPointInvalid_deletesRestaurant() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(new ArrayList<>(Arrays.asList(127.0280, null))));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchDeleteByIds(List.of(1L));
        verify(restaurantRepository, never()).batchApplySyncPatch(anyList());
        verifyNoInteractions(kakaoPlaceClient, kakaoPlaceDetailClient, kakaoPlaceMapper);
    }

    @Test
    void syncChunk_whenResolvedPointInvalid_deletesRestaurant() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "123",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailData()));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchDeleteByIds(List.of(1L));
        verify(restaurantRepository, never()).batchApplySyncPatch(anyList());
        verify(kakaoPlaceClient, never()).searchPlace("맛집", gangnam().getName());
        verify(kakaoPlaceMapper, never()).toDomainData(any());
    }

    @Test
    void syncChunk_whenKakaoPlaceNotFound_deletesRestaurant() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", gangnam(), "not-found-id",
                new GeoJson.Point(List.of(127.0280, 37.4980)));
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("not-found-id"))
                .thenReturn(KakaoPlaceDetailFetchResult.notFound());

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchDeleteByIds(List.of(1L));
        verify(restaurantRepository, never()).batchApplySyncPatch(anyList());
        verifyNoInteractions(kakaoPlaceClient, kakaoPlaceMapper);
    }

    @Test
    void syncChunk_whenTargetsMissing_returnsFailedWithoutExternalCalls() {
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L, 2L))).thenReturn(List.of());

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L, 2L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failedCount()).isEqualTo(2);
        verify(restaurantRepository, never()).batchApplySyncPatch(anyList());
        verifyNoInteractions(kakaoPlaceClient, kakaoPlaceDetailClient, kakaoPlaceMapper);
    }

    private Region gangnam() {
        return Region.of("GANGNAM", "강남역", new GeoJson.Point(List.of(127.0276, 37.4979)));
    }

    private KakaoPlaceDetailData detailData() {
        return new KakaoPlaceDetailData(
                "123",
                "맛집",
                null,
                null,
                null,
                4.3,
                "https://img",
                List.of(),
                "리뷰",
                100,
                100,
                "메뉴",
                15000,
                "₩₩",
                "요약",
                List.of("a", "b"),
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

    private KakaoPlaceDetailData detailDataWithCoordinate(String placeName, Double latitude, Double longitude) {
        return new KakaoPlaceDetailData(
                "123",
                placeName,
                null,
                latitude,
                longitude,
                4.3,
                "https://img",
                List.of(),
                "리뷰",
                100,
                100,
                "메뉴",
                15000,
                "₩₩",
                "요약",
                List.of("a", "b"),
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

    private KakaoPlaceDetailData detailDataWithCategory(String name2, String name3) {
        return new KakaoPlaceDetailData(
                "123",
                "와인코르크",
                null,
                37.498,
                127.0285,
                4.3,
                "https://img",
                List.of(),
                "리뷰",
                100,
                100,
                "메뉴",
                15000,
                "₩₩",
                "요약",
                List.of("a", "b"),
                null,
                null,
                name2,
                name3,
                null,
                null,
                null,
                null
        );
    }
}
