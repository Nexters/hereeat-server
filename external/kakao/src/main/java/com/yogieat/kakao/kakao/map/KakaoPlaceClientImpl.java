package com.yogieat.kakao.kakao.map;

import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoPlaceClientImpl implements KakaoPlaceClient {

    private final RestClient kakaoRestClient;

    @Override
    public Optional<KaKaoPlaceDocumentResult> searchPlace(String placeName, String region) {
        String normalizedName = placeName == null ? "" : placeName.trim();
        String normalizedRegion = region == null ? "" : region.trim();

        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        List<String> candidates = buildCandidateQueries(normalizedName, normalizedRegion);
        for (String query : candidates) {
            try {
                Optional<KaKaoPlaceDocumentResult> result = searchSingleQuery(query);
                if (result.isPresent()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Kakao API search failed. query={}", query, e);
            }
        }

        log.warn("No place found for name={} region={} with {} candidates",
                normalizedName, normalizedRegion, candidates.size());
        return Optional.empty();
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

    private Optional<KaKaoPlaceDocumentResult> searchSingleQuery(String query) {
        log.debug("Searching Kakao Place API with query={}", query);
        KakaoSearchResponse response = kakaoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", 1)
                        .build())
                .retrieve()
                .body(KakaoSearchResponse.class);

        if (response == null || response.documents().isEmpty()) {
            return Optional.empty();
        }

        KaKaoPlaceDocument document = response.documents().getFirst();
        log.debug("Found place via query={}, place={}", query, document.placeName());
        return Optional.of(document.toResult());
    }
}
