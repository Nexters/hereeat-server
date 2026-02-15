package com.yogieat.restaurant.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantSyncServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private KakaoPlaceClient kakaoPlaceClient;

    @Mock
    private KakaoPlaceDetailClient kakaoPlaceDetailClient;

    @Mock
    private KakaoPlaceMapper kakaoPlaceMapper;

    @InjectMocks
    private RestaurantSyncService restaurantSyncService;

    @Test
    void syncChunk_success_callsBatchApplyOnce() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", Region.GANGNAM, "123");
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailData("맛집")));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchApplySyncPatch(anyList());
    }

    @Test
    void syncChunk_whenOnlyDetailAvailable_fillsMapUrlAndLocation() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", Region.GANGNAM, "123");
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("123"))
                .thenReturn(KakaoPlaceDetailFetchResult.success(detailDataWithCoordinate("맛집", 37.51, 127.03)));

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.successCount()).isEqualTo(1);
        ArgumentCaptor<List<RestaurantSyncPatchCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(restaurantRepository).batchApplySyncPatch(captor.capture());
        RestaurantSyncPatchCommand command = captor.getValue().getFirst();
        assertThat(command.mapUrl()).isEqualTo("https://place.map.kakao.com/123");
        assertThat(command.longitude()).isEqualTo(127.03);
        assertThat(command.latitude()).isEqualTo(37.51);
    }

    @Test
    void syncChunk_whenKakaoPlaceNotFound_softDeletesRestaurant() {
        RestaurantSyncTarget target = new RestaurantSyncTarget(1L, "맛집", Region.GANGNAM, "not-found-id");
        when(restaurantRepository.findSyncTargetsByIds(List.of(1L))).thenReturn(List.of(target));
        when(kakaoPlaceDetailClient.fetchPlaceDetailResult("not-found-id"))
                .thenReturn(KakaoPlaceDetailFetchResult.notFound());

        RestaurantSyncChunkResult result = restaurantSyncService.syncChunk(List.of(1L), Runnable::run);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(restaurantRepository).batchSoftDeleteByIds(List.of(1L));
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

    private KakaoPlaceDetailData detailData(String placeName) {
        return new KakaoPlaceDetailData(
                "123",
                placeName,
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
                null
        );
    }
}
