package com.yogieat.restaurant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RestaurantAdminLookupService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantAdminLookupService.class);

    private final ObjectProvider<KakaoPlaceClient> kakaoPlaceClientProvider;
    private final ObjectProvider<KakaoPlaceDetailClient> kakaoPlaceDetailClientProvider;

    public RestaurantAdminLookupService(
            ObjectProvider<KakaoPlaceClient> kakaoPlaceClientProvider,
            ObjectProvider<KakaoPlaceDetailClient> kakaoPlaceDetailClientProvider
    ) {
        this.kakaoPlaceClientProvider = kakaoPlaceClientProvider;
        this.kakaoPlaceDetailClientProvider = kakaoPlaceDetailClientProvider;
    }

    public List<KaKaoPlaceDocumentResult> searchByKeyword(String keyword, int requestedSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        List<KaKaoPlaceDocumentResult> results = kakaoPlaceClient().searchPlaces(normalizedKeyword, null, requestedSize);
        prefetchPlaceDetails(results);
        return results;
    }

    public KakaoPlaceDetailFetchResult fetchPlaceDetail(String externalId) {
        String normalizedExternalId = externalId == null ? "" : externalId.strip();
        if (normalizedExternalId.isBlank()) {
            return KakaoPlaceDetailFetchResult.unavailable();
        }

        return kakaoPlaceDetailClient().fetchPlaceDetailResult(normalizedExternalId);
    }

    private KakaoPlaceClient kakaoPlaceClient() {
        KakaoPlaceClient client = kakaoPlaceClientProvider.getIfAvailable();
        if (client == null) {
            log.error(
                    "KakaoPlaceClient bean is not available. Check kakao.api.client.enabled and environment variable "
                            + "KAKAO_CLIENT_ENABLED"
            );
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
        return client;
    }

    private KakaoPlaceDetailClient kakaoPlaceDetailClient() {
        KakaoPlaceDetailClient client = kakaoPlaceDetailClientProvider.getIfAvailable();
        if (client == null) {
            log.error(
                    "KakaoPlaceDetailClient bean is not available. Check kakao.api.client.enabled and environment variable "
                            + "KAKAO_CLIENT_ENABLED"
            );
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
        return client;
    }

    private void prefetchPlaceDetails(List<KaKaoPlaceDocumentResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        KakaoPlaceDetailClient client = kakaoPlaceDetailClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }

        results.stream()
                .map(KaKaoPlaceDocumentResult::id)
                .filter(id -> id != null && !id.isBlank())
                .forEach(client::prefetchPlaceDetailResult);
    }
}
