package com.yogieat.restaurant.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.common.GeoJson;
import com.yogieat.common.GeoUtils;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.KakaoPlaceMapper;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchStatus;
import com.yogieat.external.kakao.result.KakaoRestaurantData;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncChunkResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Conditional(RestaurantSyncServiceCondition.class)
@RequiredArgsConstructor
@Slf4j
public class RestaurantSyncService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double SYNC_REGION_RADIUS_KM = 1.0;

    private final RestaurantRepository restaurantRepository;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoPlaceDetailClient kakaoPlaceDetailClient;
    private final KakaoPlaceMapper kakaoPlaceMapper;

    public RestaurantSyncResult syncOne(Long restaurantId) {
        RestaurantSyncChunkResult chunkResult = syncChunk(List.of(restaurantId), Runnable::run);

        if (chunkResult.successCount() == 1) {
            return RestaurantSyncResult.success(restaurantId);
        }

        String message = chunkResult.errorMessages().isEmpty()
                ? "sync failed"
                : chunkResult.errorMessages().getFirst();
        return RestaurantSyncResult.failed(restaurantId, message);
    }

    public RestaurantSyncChunkResult syncChunk(List<Long> ids, Executor executor) {
        if (ids.isEmpty()) {
            return RestaurantSyncChunkResult.of(0, 0, 0, List.of());
        }

        List<RestaurantSyncTarget> targets = restaurantRepository.findSyncTargetsByIds(ids);
        Map<Long, RestaurantSyncTarget> targetMap = new HashMap<>();
        targets.forEach(target -> targetMap.put(target.id(), target));

        List<CompletableFuture<SyncExecution>> futures = ids.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> syncTarget(id, targetMap.get(id)), executor))
                    .toList();

        List<SyncExecution> executions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        List<RestaurantSyncPatchCommand> patchCommands = executions.stream()
                .filter(SyncExecution::isPatchSuccess)
                .map(SyncExecution::patchCommand)
                .toList();
        List<Long> softDeleteIds = executions.stream()
                .filter(SyncExecution::isDeleteSuccess)
                .map(SyncExecution::restaurantId)
                .toList();

        List<String> errorMessages = new ArrayList<>();
        for (SyncExecution execution : executions) {
            if (!execution.success()) {
                if (execution.message() != null && !execution.message().isBlank() && errorMessages.size() < 10) {
                    errorMessages.add(execution.message());
                }
            }
        }

        int persistedSuccessCount = persistChunkChanges(patchCommands, softDeleteIds, errorMessages);
        int failedCount = ids.size() - persistedSuccessCount;

        return RestaurantSyncChunkResult.of(
                ids.size(),
                persistedSuccessCount,
                failedCount,
                errorMessages
        );
    }

    @Transactional
    protected int persistChunkChanges(
            List<RestaurantSyncPatchCommand> patchCommands,
            List<Long> softDeleteIds,
            List<String> errorMessages
    ) {
        int successCount = 0;

        if (!patchCommands.isEmpty()) {
            try {
                restaurantRepository.batchApplySyncPatch(patchCommands);
                successCount += patchCommands.size();
            } catch (Exception e) {
                log.error("Batch sync patch failed for {} restaurants", patchCommands.size(), e);
                if (errorMessages.size() < 10) {
                    errorMessages.add("batch update failed: " + e.getMessage());
                }
            }
        }

        if (!softDeleteIds.isEmpty()) {
            try {
                restaurantRepository.batchDeleteByIds(softDeleteIds);
                successCount += softDeleteIds.size();
            } catch (Exception e) {
                log.error("Batch delete failed for {} restaurants", softDeleteIds.size(), e);
                if (errorMessages.size() < 10) {
                    errorMessages.add("batch delete failed: " + e.getMessage());
                }
            }
        }

        return successCount;
    }

    private SyncExecution syncTarget(Long requestedId, RestaurantSyncTarget target) {
        if (target == null) {
            return SyncExecution.failed(requestedId, "restaurant not found");
        }
        if (!isWithinRegionRadius(target, null)) {
            return SyncExecution.delete(target.id());
        }

        try {
            SyncSource source = resolveSource(target);
            if (source == SyncSource.DELETE_TARGET) {
                return SyncExecution.delete(target.id());
            }
            if (source == null) {
                return SyncExecution.failed(
                        target.id(),
                        "kakao source unavailable"
                                + " (restaurantId=" + target.id()
                                + ", name=" + target.name()
                                + ", region=" + target.region().name()
                                + ", externalId=" + target.externalId()
                                + ")"
                );
            }

            RestaurantSyncPatch patch = buildPatch(source, target.externalId());
            if (!isWithinRegionRadius(target, patch.location())) {
                return SyncExecution.delete(target.id());
            }
            RestaurantSyncPatchCommand command = RestaurantSyncPatchCommand.of(target.id(), patch);
            return SyncExecution.success(target.id(), command);
        } catch (Exception e) {
            log.error("Restaurant sync failed. restaurantId={}", target.id(), e);
            return SyncExecution.failed(target.id(), e.getMessage());
        }
    }

    private SyncSource resolveSource(RestaurantSyncTarget target) {
        if (target.externalId() != null && !target.externalId().isBlank()) {
            KakaoPlaceDetailFetchResult detailResult = kakaoPlaceDetailClient.fetchPlaceDetailResult(target.externalId());
            if (detailResult.status() == KakaoPlaceDetailFetchStatus.NOT_FOUND) {
                return SyncSource.DELETE_TARGET;
            }
            if (detailResult.status() == KakaoPlaceDetailFetchStatus.SUCCESS && detailResult.detail() != null) {
                return new SyncSource(target.externalId(), null, detailResult.detail());
            }
        }

        Optional<KaKaoPlaceDocumentResult> placeOpt = kakaoPlaceClient.searchPlace(
                target.name(), target.region().getName()
        );

        if (placeOpt.isEmpty()) {
            return null;
        }

        KaKaoPlaceDocumentResult place = placeOpt.get();
        Optional<KakaoPlaceDetailData> detailOpt = kakaoPlaceDetailClient.fetchPlaceDetail(place.id());

        return new SyncSource(place.id(), place, detailOpt.orElse(null));
    }

    private RestaurantSyncPatch buildPatch(SyncSource source, String currentExternalId) {
        KakaoRestaurantData searchData = null;
        if (source.place() != null) {
            searchData = kakaoPlaceMapper.toDomainData(source.place());
        }

        KakaoPlaceDetailData detail = source.detail();

        String externalId = source.externalId() != null ? source.externalId() : currentExternalId;
        String name = detail != null && detail.placeName() != null ? detail.placeName() : null;
        String mapUrl = resolveMapUrl(externalId, searchData);
        GeoJson.Point location = resolveLocation(searchData, detail);
        Double rating = detail != null ? detail.rating() : null;
        String imageUrl = detail != null ? detail.mainPhotoUrl() : null;
        String representativeReview = detail != null ? detail.representativeReview() : null;
        Integer reviewCount = detail != null ? detail.reviewCount() : null;
        Integer blogReviewCount = detail != null ? detail.blogReviewCount() : null;
        String representMenu = detail != null ? detail.representMenu() : null;
        Integer representMenuPrice = normalizeMenuPrice(detail != null ? detail.representMenuPrice() : null);
        String priceLevel = detail != null ? detail.priceLevel() : null;
        String aiMateSummaryTitle = detail != null ? detail.aiMateSummaryTitle() : null;
        String aiMateSummaryContents = detail != null ? toJson(detail.aiMateSummaryContents()) : null;

        return new RestaurantSyncPatch(
                externalId,
                name,
                mapUrl,
                location,
                rating,
                imageUrl,
                representativeReview,
                reviewCount,
                blogReviewCount,
                representMenu,
                representMenuPrice,
                priceLevel,
                aiMateSummaryTitle,
                aiMateSummaryContents,
                detail != null ? detail.timeSlot() : null
        );
    }

    private String resolveMapUrl(String externalId, KakaoRestaurantData searchData) {
        if (searchData != null && searchData.mapUrl() != null && !searchData.mapUrl().isBlank()) {
            return normalizeMapUrl(searchData.mapUrl());
        }
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return "https://place.map.kakao.com/" + externalId;
    }

    private GeoJson.Point resolveLocation(KakaoRestaurantData searchData, KakaoPlaceDetailData detail) {
        if (searchData != null && searchData.location() != null) {
            return searchData.location();
        }
        if (detail == null || detail.longitude() == null || detail.latitude() == null) {
            return null;
        }
        return new GeoJson.Point(List.of(detail.longitude(), detail.latitude()));
    }

    private Integer normalizeMenuPrice(Integer price) {
        if (price == null || price <= 0) {
            return null;
        }
        return price;
    }

    private String toJson(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(contents);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String normalizeMapUrl(String mapUrl) {
        if (mapUrl == null || mapUrl.isBlank()) {
            return null;
        }

        if (mapUrl.startsWith("https:")) {
            return mapUrl;
        }

        if (mapUrl.startsWith("http:")) {
            return "https:" + mapUrl.substring("http:".length());
        }

        if (mapUrl.startsWith("//")) {
            return "https:" + mapUrl;
        }

        return mapUrl;
    }

    private boolean isWithinRegionRadius(RestaurantSyncTarget target, GeoJson.Point resolvedPoint) {
        if (target == null || target.region() == null || target.region().getCoordinatesStandard() == null) {
            return true;
        }

        GeoJson.Point restaurantPoint = target.location() != null ? target.location() : resolvedPoint;
        return isWithinDistance(target.region().getCoordinatesStandard(), restaurantPoint);
    }

    private boolean isWithinDistance(GeoJson.Point centerPoint, GeoJson.Point restaurantPoint) {
        if (!GeoUtils.isValidPoint(centerPoint) || !GeoUtils.isValidPoint(restaurantPoint)) {
            return true;
        }

        double distance = GeoUtils.calculateDistanceKm(centerPoint, restaurantPoint);
        return distance <= SYNC_REGION_RADIUS_KM;
    }

    private record SyncSource(String externalId, KaKaoPlaceDocumentResult place, KakaoPlaceDetailData detail) {
        private static final SyncSource DELETE_TARGET = new SyncSource(null, null, null);
    }

    private record SyncExecution(
            Long restaurantId,
            boolean success,
            boolean deleteTarget,
            RestaurantSyncPatchCommand patchCommand,
            String message
    ) {
        static SyncExecution success(Long restaurantId, RestaurantSyncPatchCommand command) {
            return new SyncExecution(restaurantId, true, false, command, null);
        }

        static SyncExecution delete(Long restaurantId) {
            return new SyncExecution(restaurantId, true, true, null, null);
        }

        static SyncExecution failed(Long restaurantId, String message) {
            return new SyncExecution(restaurantId, false, false, null, message);
        }

        boolean isPatchSuccess() {
            return success && !deleteTarget && patchCommand != null;
        }

        boolean isDeleteSuccess() {
            return success && deleteTarget;
        }
    }
}
