package com.yogieat.restaurant.result;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.Restaurant;
import java.time.LocalDateTime;
import java.util.List;

public final class RestaurantAdminResult {

    private RestaurantAdminResult() {
    }

    public record SearchItem(
            String externalId,
            String placeName,
            String addressName,
            String roadAddressName,
            String category,
            String x,
            String y
    ) {
        public static SearchItem from(KaKaoPlaceDocumentResult result) {
            return new SearchItem(
                    result.id(),
                    result.placeName(),
                    result.addressName(),
                    result.roadAddressName(),
                    result.categoryName(),
                    result.x(),
                    result.y()
            );
        }
    }

    public record Search(
            String keyword,
            List<SearchItem> items
    ) {
        public static Search of(String keyword, List<KaKaoPlaceDocumentResult> items) {
            List<SearchItem> mappedItems = items == null
                    ? List.of()
                    : items.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(SearchItem::from)
                        .toList();
            return new Search(keyword, mappedItems);
        }
    }

    public record Create(
            Long restaurantId,
            boolean duplicated
    ) {
        public static Create created(Long restaurantId) {
            return new Create(restaurantId, false);
        }

        public static Create duplicated(Long restaurantId) {
            return new Create(restaurantId, true);
        }
    }

    public record Detail(
            Long id,
            String externalId,
            Long categoryId,
            LargeCategory largeCategory,
            String mediumCategory,
            String name,
            String address,
            Double rating,
            String imageUrl,
            String mapUrl,
            String representativeReview,
            String description,
            Region region,
            GeoJson.Point location,
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            TimeSlot timeSlot,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail of(
                Long id,
                String externalId,
                Long categoryId,
                LargeCategory largeCategory,
                String mediumCategory,
                String name,
                String address,
                Double rating,
                String imageUrl,
                String mapUrl,
                String representativeReview,
                String description,
                Region region,
                GeoJson.Point location,
                Integer reviewCount,
                Integer blogReviewCount,
                String representMenu,
                Integer representMenuPrice,
                String priceLevel,
                String aiMateSummaryTitle,
                List<String> aiMateSummaryContents,
                TimeSlot timeSlot,
                LocalDateTime createdAt,
                LocalDateTime updatedAt
        ) {
            return new Detail(
                    id,
                    externalId,
                    categoryId,
                    largeCategory,
                    mediumCategory,
                    name,
                    address,
                    rating,
                    imageUrl,
                    mapUrl,
                    representativeReview,
                    description,
                    region,
                    location,
                    reviewCount,
                    blogReviewCount,
                    representMenu,
                    representMenuPrice,
                    priceLevel,
                    aiMateSummaryTitle,
                    aiMateSummaryContents,
                    timeSlot,
                    createdAt,
                    updatedAt
            );
        }

        public static Detail from(Restaurant restaurant) {
            return new Detail(
                    restaurant.id(),
                    restaurant.externalId(),
                    restaurant.categoryId(),
                    null,
                    null,
                    restaurant.name(),
                    restaurant.address(),
                    restaurant.rating(),
                    restaurant.imageUrl(),
                    restaurant.mapUrl(),
                    restaurant.representativeReview(),
                    restaurant.description(),
                    restaurant.region(),
                    restaurant.location(),
                    restaurant.reviewCount(),
                    restaurant.blogReviewCount(),
                    restaurant.representMenu(),
                    restaurant.representMenuPrice(),
                    restaurant.priceLevel(),
                    restaurant.aiMateSummaryTitle(),
                    restaurant.aiMateSummaryContents(),
                    restaurant.timeSlot(),
                    restaurant.createdAt(),
                    restaurant.updatedAt()
            );
        }
    }
}
