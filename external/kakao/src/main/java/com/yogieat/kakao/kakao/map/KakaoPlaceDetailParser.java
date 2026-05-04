package com.yogieat.kakao.kakao.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.external.kakao.result.KakaoPlaceDetailData;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public KakaoPlaceDetailParser() {
        this(Clock.system(KST));
    }

    KakaoPlaceDetailParser(Clock clock) {
        this.clock = clock;
    }

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

            // 추천 근거 데이터 추출 (신규 필드)
            Integer reviewCount = extractReviewCount(panel);
            Integer blogReviewCount = extractBlogReviewCount(panel);
            String[] representMenuData = extractRepresentMenu(panel);
            String representMenu = representMenuData[0];
            Integer representMenuPrice = representMenuData[1] != null ? Integer.parseInt(representMenuData[1]) : null;
            String priceLevel = extractPriceLevel(panel);
            String aiMateSummaryTitle = extractAiMateSummaryTitle(panel);
            List<String> aiMateSummaryContents = extractAiMateSummaryContents(panel);
            String station = extractText(panel.at("/find_way/subway"), "station_simple_name");

            // TimeSlot 추출 (폴백 전략: blog_summaries → visitor 데이터)
            TimeSlot timeSlot = extractTimeSlot(panel);

            // 카테고리 추출 (원본 name2/name3 + 매핑 결과)
            String apiCategoryName2 = extractText(panel.at("/summary/category"), "name2");
            String apiCategoryName3 = extractText(panel.at("/summary/category"), "name3");
            LargeCategory apiLargeCategory = extractApiLargeCategory(apiCategoryName2);
            String apiMediumCategory = extractApiMediumCategory(apiCategoryName3, apiLargeCategory);

            // 휴무일 추출
            List<LocalDate> offDays = extractOffDates(panel);

            log.debug("Successfully parsed place: placeId={}, rating={}, photos={}, review={}, reviewCount={}, blogReviewCount={}, timeSlot={}, apiLargeCategory={}, apiMediumCategory={}",
                    confirmId, rating, photoUrls.size(), representativeReview != null, reviewCount, blogReviewCount, timeSlot, apiLargeCategory, apiMediumCategory);

            return new KakaoPlaceDetailData(
                    confirmId,
                    placeName,
                    finalAddress,
                    latitude,
                    longitude,
                    rating,
                    mainPhotoUrl,
                    photoUrls,
                    representativeReview,
                    reviewCount,
                    blogReviewCount,
                    representMenu,
                    representMenuPrice,
                    priceLevel,
                    aiMateSummaryTitle,
                    aiMateSummaryContents,
                    station,
                    timeSlot,
                    apiCategoryName2,
                    apiCategoryName3,
                    apiLargeCategory,
                    apiMediumCategory,
                    offDays
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
        // 20자 이상 리뷰 중 최고 평점
        int maxRatingLongReview = 0;
        String bestLongReview = null;
        String bestLongReviewRegisteredAt = null;

        // 전체 리뷰 중 최고 평점 (폴백용)
        int maxRatingAny = 0;
        String bestAnyReview = null;
        String bestAnyReviewRegisteredAt = null;

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

            // 20자 이상 리뷰 트래킹
            if (contents.length() >= 20) {
                if (rating > maxRatingLongReview) {
                    maxRatingLongReview = rating;
                    bestLongReview = contents;
                    bestLongReviewRegisteredAt = registeredAt;
                } else if (rating == maxRatingLongReview && registeredAt != null && bestLongReviewRegisteredAt != null) {
                    if (registeredAt.compareTo(bestLongReviewRegisteredAt) > 0) {
                        bestLongReview = contents;
                        bestLongReviewRegisteredAt = registeredAt;
                    }
                }
            }

            // 전체 리뷰 트래킹 (폴백용)
            if (rating > maxRatingAny) {
                maxRatingAny = rating;
                bestAnyReview = contents;
                bestAnyReviewRegisteredAt = registeredAt;
            } else if (rating == maxRatingAny && registeredAt != null && bestAnyReviewRegisteredAt != null) {
                if (registeredAt.compareTo(bestAnyReviewRegisteredAt) > 0) {
                    bestAnyReview = contents;
                    bestAnyReviewRegisteredAt = registeredAt;
                }
            }
        }

        // 20자 이상 리뷰 우선, 없으면 최고 평점 리뷰
        return bestLongReview != null ? bestLongReview : bestAnyReview;
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

    /**
     * 카카오맵 리뷰 수 추출
     * JSON 경로: /kakaomap_review/score_set/review_count
     */
    private Integer extractReviewCount(JsonNode panel) {
        JsonNode scoreSet = panel.at("/kakaomap_review/score_set");
        if (scoreSet != null && !scoreSet.isMissingNode()) {
            return extractInteger(scoreSet.get("review_count"));
        }
        return null;
    }

    /**
     * 블로그 리뷰 수 추출
     * JSON 경로: /blog_review/review_count
     */
    private Integer extractBlogReviewCount(JsonNode panel) {
        JsonNode blogReview = panel.at("/blog_review");
        if (blogReview != null && !blogReview.isMissingNode()) {
            return extractInteger(blogReview.get("review_count"));
        }
        return null;
    }

    /**
     * 대표 메뉴 추출 (첫 번째 메뉴 아이템)
     * JSON 경로: /menu/menus/items[0] 또는 /menu/yogiyo_menus/items[0]
     * @return [메뉴명, 가격] 배열
     */
    private String[] extractRepresentMenu(JsonNode panel) {
        // 1순위: /menu/menus/items
        JsonNode items = panel.at("/menu/menus/items");
        if (items != null && items.isArray() && !items.isEmpty()) {
            JsonNode firstItem = items.get(0);
            String name = extractText(firstItem, "name");
            Integer price = extractInteger(firstItem.get("price"));
            if (name != null && !name.isBlank()) {
                return new String[]{name, price != null ? price.toString() : null};
            }
        }

        // 2순위: /menu/yogiyo_menus/items (폴백)
        JsonNode yogiyoItems = panel.at("/menu/yogiyo_menus/items");
        if (yogiyoItems != null && yogiyoItems.isArray() && !yogiyoItems.isEmpty()) {
            JsonNode firstItem = yogiyoItems.get(0);
            String name = extractText(firstItem, "name");
            Integer price = extractInteger(firstItem.get("price"));
            if (name != null && !name.isBlank()) {
                return new String[]{name, price != null ? price.toString() : null};
            }
        }

        return new String[]{null, null};
    }

    /**
     * 가격대 추출
     * JSON 경로: /ai_mate/price_level/symbol
     */
    private String extractPriceLevel(JsonNode panel) {
        JsonNode priceLevel = panel.at("/ai_mate/price_level");
        if (priceLevel != null && !priceLevel.isMissingNode()) {
            return extractText(priceLevel, "symbol");
        }
        return null;
    }

    /**
     * AI Mate 요약 제목 추출
     * JSON 경로: /ai_mate/summary/title
     */
    private String extractAiMateSummaryTitle(JsonNode panel) {
        JsonNode summary = panel.at("/ai_mate/summary");
        if (summary != null && !summary.isMissingNode()) {
            return extractText(summary, "title");
        }
        return null;
    }

    /**
     * AI Mate 요약 내용 리스트 추출
     * JSON 경로: /ai_mate/summary/contents
     */
    private List<String> extractAiMateSummaryContents(JsonNode panel) {
        List<String> contents = new ArrayList<>();
        JsonNode contentsNode = panel.at("/ai_mate/summary/contents");
        if (contentsNode != null && contentsNode.isArray()) {
            for (JsonNode item : contentsNode) {
                if (item.isTextual()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        contents.add(text);
                    }
                }
            }
        }
        return contents;
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

    private Integer extractInteger(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isNumber()) {
            return node.asInt();
        }

        if (node.isTextual()) {
            try {
                String normalized = node.asText().replaceAll("[^0-9-]", "");
                if (normalized.isBlank() || "-".equals(normalized)) {
                    return null;
                }
                return Integer.parseInt(normalized);
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


    /**
     * TimeSlot 추출 (폴백 전략 적용)
     * 1순위: open_hours 영업시간
     * 2순위: blog_summaries 방문 목적 키워드
     * 3순위: visitor 데이터 분석
     */
    private TimeSlot extractTimeSlot(JsonNode panel) {
        // 1순위: open_hours 영업시간
        TimeSlot fromOpenHours = extractTimeSlotFromOpenHours(panel);
        if (fromOpenHours != null) {
            return fromOpenHours;
        }

        // 2순위: blog_summaries 방문 목적 키워드
        TimeSlot fromKeywords = extractTimeSlotFromBlogSummaries(panel);
        if (fromKeywords != null) {
            return fromKeywords;
        }

        // 3순위: visitor 데이터 분석
        return extractTimeSlotFromVisitorData(panel);
    }


    /**
     * open_hours에서 영업시간 추출하여 TimeSlot 판단
     * JSON 경로: /open_hours/week_from_today/week_periods[0]/days[0]/on_days/start_end_time_desc
     * 예: "11:00 ~ 22:00"
     */
    private TimeSlot extractTimeSlotFromOpenHours(JsonNode panel) {
        JsonNode openHours = panel.path("open_hours");
        if (openHours == null || openHours.isMissingNode()) {
            return null;
        }

        JsonNode weekPeriods = openHours.path("week_from_today").path("week_periods");
        if (!weekPeriods.isArray() || weekPeriods.isEmpty()) {
            return null;
        }

        JsonNode days = weekPeriods.get(0).path("days");
        if (!days.isArray() || days.isEmpty()) {
            return null;
        }

        String timeDesc = days.get(0).path("on_days").path("start_end_time_desc").asText(null);
        if (timeDesc == null || timeDesc.isBlank()) {
            return null;
        }

        return parseTimeRange(timeDesc);
    }

    /**
     * 영업시간 문자열을 파싱하여 TimeSlot 결정
     * @param timeDesc "11:00 ~ 22:00" 형태의 문자열
     * @return TimeSlot (LUNCH, DINNER, BOTH, or null)
     */
    private TimeSlot parseTimeRange(String timeDesc) {
        try {
            String[] parts = timeDesc.split("~");
            if (parts.length != 2) {
                return null;
            }

            int openHour = parseHour(parts[0].trim());
            int closeHour = parseHour(parts[1].trim());

            // LUNCH: 12시 이전 오픈, 16시 이후 마감
            boolean coversLunch = openHour <= 12 && closeHour >= 16;
            // DINNER: 20시 이전 오픈, 21시 이후 마감 (18시 오픈 저녁 전용 맛집 포함)
            boolean coversDinner = openHour <= 20 && closeHour >= 21;

            if (coversLunch && coversDinner) {
                return TimeSlot.BOTH;
            } else if (coversLunch) {
                return TimeSlot.LUNCH;
            } else if (coversDinner) {
                return TimeSlot.DINNER;
            }
            return null;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse time range: {}", timeDesc);
            return null;
        }
    }

    /**
     * 시간 문자열에서 시(hour) 추출
     * @param time "11:00" 형태의 문자열
     * @return 시간 (0-23)
     */
    private int parseHour(String time) {
        return Integer.parseInt(time.split(":")[0]);
    }

    /**
     * blog_summaries에서 방문 목적 키워드 추출
     * JSON 경로: /ai_mate/blog_summaries[title="방문 목적"]/keywords
     */
    private TimeSlot extractTimeSlotFromBlogSummaries(JsonNode panel) {
        JsonNode blogSummaries = panel.at("/ai_mate/blog_summaries");
        if (blogSummaries == null || !blogSummaries.isArray()) {
            return null;
        }

        for (JsonNode summary : blogSummaries) {
            String title = extractText(summary, "title");
            if ("방문 목적".equals(title)) {
                JsonNode keywords = summary.get("keywords");
                if (keywords != null && keywords.isArray()) {
                    return mapKeywordsToTimeSlot(keywords);
                }
            }
        }
        return null;
    }

    /**
     * 키워드를 TimeSlot으로 매핑
     */
    private TimeSlot mapKeywordsToTimeSlot(JsonNode keywords) {
        boolean hasLunch = false;
        boolean hasDinner = false;

        for (JsonNode keyword : keywords) {
            String text = keyword.asText();
            if ("점심식사".equals(text) || "해장".equals(text)) {
                hasLunch = true;
            }
            if ("저녁식사".equals(text)) {
                hasDinner = true;
            }
        }

        if (hasLunch && hasDinner) {
            return TimeSlot.BOTH;
        }
        if (hasLunch) {
            return TimeSlot.LUNCH;
        }
        if (hasDinner) {
            return TimeSlot.DINNER;
        }
        return null;
    }

    /**
     * visitor 데이터에서 TimeSlot 분석
     * JSON 경로: /visitor/weekly_uv_average
     * 점심(11-14시) vs 저녁(17-21시) 방문자 합계 비교
     */
    private TimeSlot extractTimeSlotFromVisitorData(JsonNode panel) {
        JsonNode weeklyAvg = panel.at("/visitor/weekly_uv_average");
        if (weeklyAvg == null || !weeklyAvg.isArray() || weeklyAvg.size() < 24) {
            return null;
        }

        int lunchSum = 0;
        int dinnerSum = 0;
        int lunchValidCount = 0;
        int dinnerValidCount = 0;

        // 점심 (11-14시)
        for (int h = 11; h <= 14; h++) {
            int val = weeklyAvg.get(h).asInt(-1);
            if (val > 0) {
                lunchSum += val;
                lunchValidCount++;
            }
        }

        // 저녁 (17-21시)
        for (int h = 17; h <= 21; h++) {
            int val = weeklyAvg.get(h).asInt(-1);
            if (val > 0) {
                dinnerSum += val;
                dinnerValidCount++;
            }
        }

        // 유효 데이터 없으면 null
        if (lunchValidCount == 0 && dinnerValidCount == 0) {
            return null;
        }

        // 1.5배 이상 차이로 우세 판단
        if (lunchSum > dinnerSum * 1.5) {
            return TimeSlot.LUNCH;
        }
        if (dinnerSum > lunchSum * 1.5) {
            return TimeSlot.DINNER;
        }
        return TimeSlot.BOTH;
    }

    private List<LocalDate> extractOffDates(JsonNode panel) {
        JsonNode openHours = panel.path("open_hours");
        if (openHours.isMissingNode() || openHours.isNull()) {
            return List.of();
        }

        JsonNode weekPeriods = openHours.path("week_from_today").path("week_periods");
        if (!weekPeriods.isArray() || weekPeriods.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        Set<LocalDate> offDates = new HashSet<>();
        for (JsonNode period : weekPeriods) {
            JsonNode days = period.path("days");
            if (!days.isArray()) {
                continue;
            }
            for (JsonNode day : days) {
                if (!isOffDay(day)) {
                    continue;
                }
                String dayDesc = extractText(day, "day_of_the_week_desc");
                if (dayDesc == null) {
                    continue;
                }
                LocalDate date = parseOffDayDate(dayDesc, currentYear, currentMonth);
                if (date != null) {
                    offDates.add(date);
                }
            }
        }
        return new ArrayList<>(offDates);
    }

    private boolean isOffDay(JsonNode day) {
        JsonNode offDaysDesc = day.get("off_days_desc");
        if (offDaysDesc == null || offDaysDesc.isNull() || offDaysDesc.isMissingNode()) {
            return false;
        }
        return "휴무일".equals(offDaysDesc.asText());
    }

    private LocalDate parseOffDayDate(String dayDesc, int year, int currentMonth) {
        try {
            int start = dayDesc.indexOf('(');
            int end = dayDesc.indexOf(')');
            if (start < 0 || end <= start) {
                return null;
            }
            String[] parts = dayDesc.substring(start + 1, end).split("/");
            if (parts.length != 2) {
                return null;
            }
            int month = Integer.parseInt(parts[0].trim());
            int day = Integer.parseInt(parts[1].trim());
            // 12월 말 → 1월 초 경계 처리
            int adjustedYear = (currentMonth == 12 && month == 1) ? year + 1 : year;
            return LocalDate.of(adjustedYear, month, day);
        } catch (Exception e) {
            log.debug("Failed to parse off day date: {}", dayDesc);
            return null;
        }
    }

    /**
     * API 응답의 name2 문자열을 LargeCategory로 매핑합니다.
     */
    private LargeCategory extractApiLargeCategory(String name2) {
        if (name2 == null || name2.isBlank()) {
            return null;
        }
        return LargeCategory.fromDisplayName(name2);
    }

    /**
     * API 응답의 name3 문자열을 mediumCategory로 반환합니다.
     * LargeCategory 매핑에 실패한 경우에는 null을 반환합니다.
     */
    private String extractApiMediumCategory(String name3, LargeCategory apiLargeCategory) {
        if (apiLargeCategory == null) {
            return null;
        }
        return name3;
    }
}
