package com.yogieat.external.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for Kakao Place API panel3 response
 * Extracts detailed information including rating and photos
 */
@Component
@Slf4j
public class KakaoPlaceDetailParser {

    private static final int MAX_PHOTOS = 15;

    /**
     * Parse panel3 response to extract detailed place information
     *
     * @param panel JSON response from panel3 API
     * @param requestedPlaceId The place ID that was requested
     * @return KakaoPlaceDetailData containing rating and photos
     */
    public KakaoPlaceDetailData parse(JsonNode panel, String requestedPlaceId) {
        try {
            // 1. Extract basic information
            String confirmId = extractText(panel.at("/summary"), "confirm_id");
            if (confirmId == null || confirmId.isBlank()) {
                log.warn("panel3 response missing confirm_id for placeId: {}", requestedPlaceId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            // Validate confirm_id matches requested place ID
            if (!requestedPlaceId.equals(confirmId)) {
                log.warn("confirm_id mismatch: requested={}, response={}", requestedPlaceId, confirmId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            String placeName = extractText(panel.at("/summary"), "name");

            // Address priority: road → disp → jibun
            JsonNode addr = panel.at("/summary/address");
            String addressRoad = extractText(addr, "road");
            String addressDisp = extractText(addr, "disp");
            String addressJibun = extractText(addr, "jibun");
            String finalAddress = firstNonBlank(addressRoad, addressDisp, addressJibun);

            // Coordinates
            Double latitude = extractDouble(panel.at("/summary/point/lat"));
            Double longitude = extractDouble(panel.at("/summary/point/lon"));

            // 2. Extract rating
            Double rating = extractRating(panel);

            // 3. Extract photos
            List<String> photoUrls = extractPhotos(panel);
            String mainPhotoUrl = photoUrls.isEmpty() ? null : photoUrls.getFirst();

            return new KakaoPlaceDetailData(
                    confirmId,
                    placeName,
                    finalAddress,
                    latitude,
                    longitude,
                    rating,
                    mainPhotoUrl,
                    photoUrls
            );

        } catch (Exception e) {
            log.error("Failed to parse panel3 response for placeId: {}", requestedPlaceId, e);
            return KakaoPlaceDetailData.empty(requestedPlaceId);
        }
    }

    /**
     * Extract rating from panel3 response
     * Priority: /kakaomap_review/score_set/average_score → /scoreInfo/scoreSum → /basicInfo/feedback/scoreSum
     */
    private Double extractRating(JsonNode panel) {
        // Try /kakaomap_review/score_set/average_score first (most reliable from actual reviews)
        JsonNode kakaoReview = panel.at("/kakaomap_review/score_set");
        if (kakaoReview != null && !kakaoReview.isMissingNode()) {
            Double rating = extractDouble(kakaoReview.get("average_score"));
            if (rating != null && rating > 0) {
                log.debug("Extracted rating from kakaomap_review: {}", rating);
                return rating;
            }
        }

        // Fallback to /scoreInfo/scoreSum
        JsonNode scoreInfo = panel.at("/scoreInfo");
        if (scoreInfo != null && !scoreInfo.isMissingNode()) {
            Double rating = extractDouble(scoreInfo.get("scoreSum"));
            if (rating != null && rating > 0) {
                log.debug("Extracted rating from scoreInfo: {}", rating);
                return rating;
            }
        }

        // Fallback to /basicInfo/feedback/scoreSum
        JsonNode feedback = panel.at("/basicInfo/feedback");
        if (feedback != null && !feedback.isMissingNode()) {
            Double rating = extractDouble(feedback.get("scoreSum"));
            if (rating != null && rating > 0) {
                log.debug("Extracted rating from basicInfo/feedback: {}", rating);
                return rating;
            }
        }

        return null;
    }

    /**
     * Extract photos from panel3 response (max 15 photos)
     * Sources:
     * 1) /menu/menus/photos[*]
     * 2) /photos/photos[*]
     * 3) /blog_review/reviews[*].photos[*]
     */
    private List<String> extractPhotos(JsonNode panel) {
        List<String> photoUrls = new ArrayList<>();
        Set<String> dedup = new HashSet<>();

        // 1) Menu photos
        extractMenuPhotos(panel, photoUrls, dedup);

        // 2) Global photos
        if (photoUrls.size() < MAX_PHOTOS) {
            extractGlobalPhotos(panel, photoUrls, dedup);
        }

        // 3) Blog review photos
        if (photoUrls.size() < MAX_PHOTOS) {
            extractBlogReviewPhotos(panel, photoUrls, dedup);
        }

        return photoUrls;
    }

    private void extractMenuPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode menus = panel.at("/menu/menus");
        if (menus != null && menus.isArray()) {
            for (JsonNode group : menus) {
                if (photoUrls.size() >= MAX_PHOTOS) break;
                JsonNode menuPhotos = group.path("photos");
                if (menuPhotos != null && menuPhotos.isArray()) {
                    for (JsonNode p : menuPhotos) {
                        if (photoUrls.size() >= MAX_PHOTOS) break;
                        addPhotoUrl(p, photoUrls, dedup);
                    }
                }
            }
        }
    }

    private void extractGlobalPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode globalPhotos = panel.at("/photos/photos");
        if (globalPhotos != null && globalPhotos.isArray()) {
            for (JsonNode p : globalPhotos) {
                if (photoUrls.size() >= MAX_PHOTOS) break;
                addPhotoUrl(p, photoUrls, dedup);
            }
        }
    }

    private void extractBlogReviewPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode reviews = panel.at("/blog_review/reviews");
        if (reviews != null && reviews.isArray()) {
            for (JsonNode r : reviews) {
                if (photoUrls.size() >= MAX_PHOTOS) break;
                JsonNode reviewPhotos = r.path("photos");
                if (reviewPhotos != null && reviewPhotos.isArray()) {
                    for (JsonNode p : reviewPhotos) {
                        if (photoUrls.size() >= MAX_PHOTOS) break;
                        addPhotoUrl(p, photoUrls, dedup);
                    }
                }
            }
        }
    }

    private void addPhotoUrl(JsonNode photoNode, List<String> photoUrls, Set<String> dedup) {
        String url = extractText(photoNode, "photo_url");
        if (url == null || url.isBlank()) {
            url = extractText(photoNode, "url");
        }

        if (url != null && !url.isBlank() && !dedup.contains(url)) {
            photoUrls.add(url);
            dedup.add(url);
        }
    }

    // Utility methods

    private String extractText(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && fieldNode.isTextual()) ? fieldNode.asText() : null;
    }

    private Double extractDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
