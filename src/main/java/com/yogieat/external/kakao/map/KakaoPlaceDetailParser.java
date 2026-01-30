package com.yogieat.external.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카카오 장소 API panel3 응답 파서
 * 평점 및 사진을 포함한 상세 정보 추출
 */
@Component
@Slf4j
public class KakaoPlaceDetailParser {

    private static final int MAX_PHOTOS = 15;

    /**
     * panel3 응답을 파싱하여 상세 장소 정보 추출
     *
     * @param panel panel3 API로부터의 JSON 응답
     * @param requestedPlaceId 요청된 장소 ID
     * @return 평점 및 사진을 포함한 KakaoPlaceDetailData
     */
    public KakaoPlaceDetailData parse(JsonNode panel, String requestedPlaceId) {
        try {
            // 1. 음식점 카테고리 확인
            String placeRestaurant = extractText(panel.at("/summary/category"), "name1");
            if (placeRestaurant == null || !"음식점".equals(placeRestaurant)) {
                log.debug("Filtered out non-restaurant place: placeId={}", requestedPlaceId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            // 2. confirm_id 기본 검증
            String confirmId = extractText(panel.at("/summary"), "confirm_id");
            if (confirmId == null || confirmId.isBlank()) {
                log.warn("panel3 response missing confirm_id for placeId: {}", requestedPlaceId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            // confirm_id가 요청한 장소 ID와 일치하는지 검증
            if (!requestedPlaceId.equals(confirmId)) {
                log.warn("confirm_id mismatch: requested={}, response={}", requestedPlaceId, confirmId);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            // 3. 평점 추출 및 필터링 (우선 처리)
            Double rating = extractRating(panel);

            // 평점이 3.0~5.0 사이가 아니면 필터링
            if (rating == null || rating < 3.0 || rating > 5.0) {
                log.debug("Filtered out place due to rating: placeId={}, rating={}", requestedPlaceId, rating);
                return KakaoPlaceDetailData.empty(requestedPlaceId);
            }

            // 4. 필터링 통과 후 상세 데이터 파싱
            String placeName = extractText(panel.at("/summary"), "name");

            // 주소 우선순위: 도로명 → 표시용 → 지번
            JsonNode addr = panel.at("/summary/address");
            String addressRoad = extractText(addr, "road");
            String addressDisp = extractText(addr, "disp");
            String addressJibun = extractText(addr, "jibun");
            String finalAddress = firstNonBlank(addressRoad, addressDisp, addressJibun);

            // 좌표
            Double latitude = extractDouble(panel.at("/summary/point/lat"));
            Double longitude = extractDouble(panel.at("/summary/point/lon"));

            // 5. 사진 추출
            List<String> photoUrls = extractPhotos(panel);
            String mainPhotoUrl = photoUrls.isEmpty() ? null : photoUrls.getFirst();

            // 6. 대표 리뷰 추출
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

    /**
     * panel3 응답에서 평점 추출
     * 우선순위: /kakaomap_review/score_set/average_score → /scoreInfo/scoreSum → /basicInfo/feedback/scoreSum
     */
    private Double extractRating(JsonNode panel) {
        // 1. 먼저 /kakaomap_review/score_set/average_score 시도 (실제 리뷰에서 가장 신뢰할 수 있음)
        JsonNode kakaoReview = panel.at("/kakaomap_review/score_set");
        if (kakaoReview != null && !kakaoReview.isMissingNode()) {
            Double rating = extractDouble(kakaoReview.get("average_score"));
            if (rating != null && rating > 0) {
                log.debug("Extracted rating from kakaomap_review: {}", rating);
                return rating;
            }
        }

        // 2. /scoreInfo/scoreSum으로 대체
        JsonNode scoreInfo = panel.at("/scoreInfo");
        if (scoreInfo != null && !scoreInfo.isMissingNode()) {
            Double rating = extractDouble(scoreInfo.get("scoreSum"));
            if (rating != null && rating > 0) {
                log.debug("Extracted rating from scoreInfo: {}", rating);
                return rating;
            }
        }

        // 3. /basicInfo/feedback/scoreSum으로 대체
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
     * panel3 응답에서 대표 리뷰 추출
     * 우선순위:
     * 1) kakaomap_review/reviews - star_rating이 가장 높고 최근 리뷰
     * 2) blog_review/reviews[0] - 블로그 리뷰 첫 번째
     */
    private String extractRepresentativeReview(JsonNode panel) {
        // 1. 카카오맵 리뷰에서 추출
        JsonNode kakaoReviews = panel.at("/kakaomap_review/reviews");
        if (kakaoReviews != null && kakaoReviews.isArray() && !kakaoReviews.isEmpty()) {
            String bestReview = findBestReview(kakaoReviews);
            if (bestReview != null && !bestReview.isBlank()) {
                log.debug("Extracted representative review from kakaomap_review");
                return bestReview;
            }
        }

        // 2. 블로그 리뷰에서 추출
        JsonNode blogReviews = panel.at("/blog_review/reviews");
        if (blogReviews != null && blogReviews.isArray() && !blogReviews.isEmpty()) {
            JsonNode firstBlogReview = blogReviews.get(0);
            String contents = extractText(firstBlogReview, "contents");
            if (contents != null && !contents.isBlank()) {
                log.debug("Extracted representative review from blog_review");
                return contents;
            }
        }

        return null;
    }

    /**
     * 리뷰 배열에서 star_rating이 가장 높고 최근 리뷰의 contents 찾기
     */
    private String findBestReview(JsonNode reviews) {
        int maxRating = 0;
        String latestReviewContents = null;
        String latestRegisteredAt = null;

        for (JsonNode review : reviews) {
            // star_rating 추출
            JsonNode ratingNode = review.get("star_rating");
            if (ratingNode == null || !ratingNode.isNumber()) {
                continue;
            }
            int rating = ratingNode.asInt();

            // contents 추출
            String contents = extractText(review, "contents");
            if (contents == null || contents.isBlank()) {
                continue;
            }

            // registered_at 추출
            String registeredAt = extractText(review, "registered_at");

            // 더 높은 평점을 찾은 경우
            if (rating > maxRating) {
                maxRating = rating;
                latestReviewContents = contents;
                latestRegisteredAt = registeredAt;
            }
            // 같은 평점이면 더 최근 리뷰 선택
            else if (rating == maxRating && registeredAt != null && latestRegisteredAt != null) {
                if (registeredAt.compareTo(latestRegisteredAt) > 0) {
                    latestReviewContents = contents;
                    latestRegisteredAt = registeredAt;
                }
            }
        }

        return latestReviewContents;
    }

    /**
     * panel3 응답에서 사진 추출 (최대 15장)
     * 우선순위:
     * 1) /menu/menus/photos[*] - 메뉴 블로그 사진
     * 2) /menu/yogiyo_menus/items[*] - 요기요 메뉴 사진
     * 3) /photos/photos[*] - POI 공식 사진
     * 4) /blog_review/reviews[*].photos[*] - 블로그 리뷰 사진
     */
    private List<String> extractPhotos(JsonNode panel) {
        List<String> photoUrls = new ArrayList<>();
        Set<String> dedup = new HashSet<>();

        // 1) 메뉴 블로그 사진 (최우선)
        extractMenuPhotos(panel, photoUrls, dedup);

        // 2) 요기요 메뉴 사진
        if (photoUrls.size() < MAX_PHOTOS) {
            extractYogiyoMenuPhotos(panel, photoUrls, dedup);
        }

        // 3) POI 공식 사진
        if (photoUrls.size() < MAX_PHOTOS) {
            extractGlobalPhotos(panel, photoUrls, dedup);
        }

        // 4) 블로그 리뷰 사진
        if (photoUrls.size() < MAX_PHOTOS) {
            extractBlogReviewPhotos(panel, photoUrls, dedup);
        }

        return photoUrls;
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

    private void extractYogiyoMenuPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode yogiyoItems = panel.at("/menu/yogiyo_menus/items");
        if (yogiyoItems != null && yogiyoItems.isArray()) {
            for (JsonNode item : yogiyoItems) {
                if (photoUrls.size() >= MAX_PHOTOS) break;
                String photoUrl = extractText(item, "photo_url");
                if (photoUrl != null && !photoUrl.isBlank() && !dedup.contains(photoUrl)) {
                    photoUrls.add(photoUrl);
                    dedup.add(photoUrl);
                }
            }
        }
    }

    private void extractMenuPhotos(JsonNode panel, List<String> photoUrls, Set<String> dedup) {
        JsonNode menuPhotos = panel.at("/menu/menus/photos");
        if (menuPhotos != null && menuPhotos.isArray()) {
            for (JsonNode p : menuPhotos) {
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

    // 유틸리티 메서드
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
