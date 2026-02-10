package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KakaoPlaceDetailParser {

    private static final int MAX_PHOTOS = 15;

    public KakaoPlaceDetailData parse(JsonNode panel, String requestedPlaceId) {
        try {
            String placeRestaurant = extractText(panel.at("/summary/category"), "name1");
            if (placeRestaurant == null || !"음식점".equals(placeRestaurant)) {
                log.debug("Filtered out non-restaurant place: placeId={}", requestedPlaceId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            String confirmId = extractText(panel.at("/summary"), "confirm_id");
            if (confirmId == null || confirmId.isBlank()) {
                log.warn("panel3 response missing confirm_id for placeId: {}", requestedPlaceId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            if (!requestedPlaceId.equals(confirmId)) {
                log.warn("confirm_id mismatch: requested={}, response={}", requestedPlaceId, confirmId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            Double rating = extractRating(panel);
            if (rating == null || rating < 3.5 || rating > 5.0) {
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            String placeName = extractText(panel.at("/summary"), "name");
            JsonNode addr = panel.at("/summary/address");
            String addressRoad = extractText(addr, "road");
            String addressDisp = extractText(addr, "disp");
            String addressJibun = extractText(addr, "jibun");
            String finalAddress = firstNonBlank(addressRoad, addressDisp, addressJibun);

            Double latitude = extractDouble(panel.at("/summary/point/lat"));
            Double longitude = extractDouble(panel.at("/summary/point/lon"));

            List<String> photoUrls = extractPhotos(panel);
            String mainPhotoUrl = photoUrls.isEmpty() ? null : photoUrls.getFirst();
            String representativeReview = extractRepresentativeReview(panel);

            log.debug("Successfully parsed place: placeId={}, rating={}, photos={}, review={}",
                    confirmId, rating, photoUrls.size(), representativeReview != null);

            return new KakaoPlaceDetailData(
                    confirmId,
                    placeName,
                    finalAddress,
                    latitude,
                    longitude,
                    rating,
                    mainPhotoUrl,
                    photoUrls,
                    representativeReview
            );
        } catch (Exception e) {
            log.error("Failed to parse panel3 response for placeId: {}", requestedPlaceId, e);
            return KakaoPlaceDetailData.empty(requestedPlaceId);
        }
    }

    private Double extractRating(JsonNode panel) {
        JsonNode kakaoReview = panel.at("/kakaomap_review/score_set");
        if (kakaoReview != null && !kakaoReview.isMissingNode()) {
            Double rating = extractDouble(kakaoReview.get("average_score"));
            if (rating != null && rating > 0) {
                return rating;
            }
        }

        JsonNode scoreInfo = panel.at("/scoreInfo");
        if (scoreInfo != null && !scoreInfo.isMissingNode()) {
            Double rating = extractDouble(scoreInfo.get("scoreSum"));
            if (rating != null && rating > 0) {
                return rating;
            }
        }

        JsonNode feedback = panel.at("/basicInfo/feedback");
        if (feedback != null && !feedback.isMissingNode()) {
            Double rating = extractDouble(feedback.get("scoreSum"));
            if (rating != null && rating > 0) {
                return rating;
            }
        }
        return null;
    }

    private String extractRepresentativeReview(JsonNode panel) {
        JsonNode kakaoReviews = panel.at("/kakaomap_review/reviews");
        if (kakaoReviews != null && kakaoReviews.isArray() && !kakaoReviews.isEmpty()) {
            String bestReview = findBestReview(kakaoReviews);
            if (bestReview != null && !bestReview.isBlank()) {
                return bestReview;
            }
        }

        JsonNode blogReviews = panel.at("/blog_review/reviews");
        if (blogReviews != null && blogReviews.isArray() && !blogReviews.isEmpty()) {
            JsonNode firstBlogReview = blogReviews.get(0);
            String contents = extractText(firstBlogReview, "contents");
            if (contents != null && !contents.isBlank()) {
                return contents;
            }
        }
        return null;
    }

    private String findBestReview(JsonNode reviews) {
        int maxRating = 0;
        String latestReviewContents = null;
        String latestRegisteredAt = null;

        for (JsonNode review : reviews) {
            JsonNode ratingNode = review.get("star_rating");
            if (ratingNode == null || !ratingNode.isNumber()) {
                continue;
            }
            int rating = ratingNode.asInt();
            String contents = extractText(review, "contents");
            if (contents == null || contents.isBlank()) {
                continue;
            }
            String registeredAt = extractText(review, "registered_at");

            if (rating > maxRating) {
                maxRating = rating;
                latestReviewContents = contents;
                latestRegisteredAt = registeredAt;
            } else if (rating == maxRating && registeredAt != null && latestRegisteredAt != null) {
                if (registeredAt.compareTo(latestRegisteredAt) > 0) {
                    latestReviewContents = contents;
                    latestRegisteredAt = registeredAt;
                }
            }
        }
        return latestReviewContents;
    }

    private List<String> extractPhotos(JsonNode panel) {
        List<String> photoUrls = new ArrayList<>();
        Set<String> dedup = new HashSet<>();
        extractMenuPhotos(panel, photoUrls, dedup);
        if (photoUrls.size() < MAX_PHOTOS) {
            extractYogiyoMenuPhotos(panel, photoUrls, dedup);
        }
        if (photoUrls.size() < MAX_PHOTOS) {
            extractGlobalPhotos(panel, photoUrls, dedup);
        }
        if (photoUrls.size() < MAX_PHOTOS) {
            extractBlogReviewPhotos(panel, photoUrls, dedup);
        }
        return photoUrls;
    }

    private void extractGlobalPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode globalPhotos = panel.at("/photos/photos");
        if (globalPhotos != null && globalPhotos.isArray()) {
            for (JsonNode p : globalPhotos) {
                if (photoUrls.size() >= MAX_PHOTOS) {
                    break;
                }
                addPhotoUrl(p, photoUrls, dedup);
            }
        }
    }

    private void extractYogiyoMenuPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode yogiyoItems = panel.at("/menu/yogiyo_menus/items");
        if (yogiyoItems != null && yogiyoItems.isArray()) {
            for (JsonNode item : yogiyoItems) {
                if (photoUrls.size() >= MAX_PHOTOS) {
                    break;
                }
                addPhotoUrl(item, photoUrls, dedup);
            }
        }
    }

    private void extractMenuPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode menus = panel.at("/menu/menus");
        if (menus == null || !menus.isArray()) {
            return;
        }

        for (JsonNode menu : menus) {
            if (photoUrls.size() >= MAX_PHOTOS) {
                break;
            }

            JsonNode photos = menu.get("photos");
            if (photos == null || !photos.isArray()) {
                continue;
            }

            for (JsonNode photo : photos) {
                if (photoUrls.size() >= MAX_PHOTOS) {
                    break;
                }
                addPhotoUrl(photo, photoUrls, dedup);
            }
        }
    }

    private void extractBlogReviewPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode reviews = panel.at("/blog_review/reviews");
        if (reviews == null || !reviews.isArray()) {
            return;
        }

        for (JsonNode review : reviews) {
            if (photoUrls.size() >= MAX_PHOTOS) {
                break;
            }

            JsonNode photos = review.get("photos");
            if (photos == null || !photos.isArray()) {
                continue;
            }

            for (JsonNode photo : photos) {
                if (photoUrls.size() >= MAX_PHOTOS) {
                    break;
                }
                addPhotoUrl(photo, photoUrls, dedup);
            }
        }
    }

    private void addPhotoUrl(JsonNode node, List<String> photoUrls, Set<String> dedup) {
        String[] candidateFields = {"url", "thumbnailUrl", "orgurl", "orgUrl", "photoUrl"};
        for (String field : candidateFields) {
            String value = extractText(node, field);
            if (value != null && !value.isBlank() && dedup.add(value)) {
                photoUrls.add(value);
                return;
            }
        }
    }

    private String extractText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Double extractDouble(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isNumber()) {
            return node.asDouble();
        }

        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
