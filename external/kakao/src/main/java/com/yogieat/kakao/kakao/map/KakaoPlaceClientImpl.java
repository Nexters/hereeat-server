package com.yogieat.kakao.kakao.map;

import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "kakao.api.client.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class KakaoPlaceClientImpl implements KakaoPlaceClient {

    private static final int KAKAO_MAX_RESULT_LIMIT = 15;

    private final RestClient kakaoRestClient;
    private final KakaoAdminKakaoApiExecutor kakaoAdminKakaoApiExecutor;

    @Override
    public List<KaKaoPlaceDocumentResult> searchPlaces(String placeName, String region, int size) {
        return kakaoAdminKakaoApiExecutor.executeSearchPlaces(
                placeName,
                region,
                size,
                () -> searchPlacesWithoutPolicy(placeName, region, size)
        );
    }

    private List<KaKaoPlaceDocumentResult> searchPlacesWithoutPolicy(
            String placeName,
            String region,
            int size
    ) {
        String normalizedName = placeName == null ? "" : placeName.trim();
        String normalizedRegion = region == null ? "" : region.trim();
        int normalizedSize = Math.max(1, Math.min(size, KAKAO_MAX_RESULT_LIMIT));
        RuntimeException lastError = null;

        if (normalizedName.isBlank()) {
            return List.of();
        }

        List<String> candidates = buildCandidateQueries(normalizedName, normalizedRegion);
        List<KaKaoPlaceDocumentResult> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String query : candidates) {
            try {
                List<KaKaoPlaceDocumentResult> queryResults = searchSingleQuery(query, normalizedSize);
                for (KaKaoPlaceDocumentResult candidate : queryResults) {
                    if (candidate == null || candidate.id() == null || candidate.id().isBlank()) {
                        continue;
                    }
                    if (seenIds.add(candidate.id())) {
                        results.add(candidate);
                        if (results.size() >= normalizedSize) {
                            return results;
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Kakao API search failed. query={}", query, e);
                lastError = e;
            }
        }

        if (results.isEmpty() && lastError != null) {
            throw lastError;
        }

        if (results.isEmpty()) {
            log.warn("No place found for name={} region={} with {} candidates",
                    normalizedName, normalizedRegion, candidates.size());
        }

        return results;
    }

    @Override
    public Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region) {
        List<KaKaoPlaceDocumentResult> results = searchPlaces(placeName, region, 1);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    private List<String> buildCandidateQueries(String placeName, String region) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String firstToken = placeName.split(" ")[0].trim();

        unique.add(joinQuery(placeName, region));
        if (!firstToken.equals(placeName)) {
            unique.add(joinQuery(firstToken, region));
        }
        unique.add(placeName);
        unique.add(firstToken);

        List<String> candidates = new ArrayList<>();
        unique.stream()
                .filter(query -> query != null && !query.isBlank())
                .forEach(candidates::add);
        return candidates;
    }

    private String joinQuery(String name, String region) {
        if (region == null || region.isBlank()) {
            return name;
        }
        return name + " " + region;
    }

    private List<KaKaoPlaceDocumentResult> searchSingleQuery(String query, int size) {
        int fetchSize = Math.max(1, Math.min(size, KAKAO_MAX_RESULT_LIMIT));
        log.debug("Searching Kakao Place API with query={}, size={}", query, fetchSize);
        KakaoSearchResponse response = kakaoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", fetchSize)
                        .build())
                .retrieve()
                .body(KakaoSearchResponse.class);

        if (response == null || response.documents().isEmpty()) {
            return List.of();
        }

        List<KaKaoPlaceDocumentResult> documents = response.documents().stream()
                .map(KaKaoPlaceDocument::toResult)
                .toList();
        log.debug("Found {} places via query={}", documents.size(), query);
        return documents;
    }
}
