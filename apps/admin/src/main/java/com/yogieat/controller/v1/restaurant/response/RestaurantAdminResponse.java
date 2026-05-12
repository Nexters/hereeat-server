package com.yogieat.controller.v1.restaurant.response;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import java.time.LocalDateTime;
import java.util.List;

public final class RestaurantAdminResponse {

    private RestaurantAdminResponse() {
    }

    public record Search(
            String keyword,
            List<SearchItem> items
    ) {
        public static Search from(RestaurantAdminResult.Search result) {
            if (result == null) {
                return null;
            }
            return new Search(
                    result.keyword(),
                    result.items().stream()
                            .map(SearchItem::from)
                            .toList()
            );
        }
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
        public static SearchItem from(RestaurantAdminResult.SearchItem item) {
            return new SearchItem(
                    item.externalId(),
                    item.placeName(),
                    item.addressName(),
                    item.roadAddressName(),
                    item.category(),
                    item.x(),
                    item.y()
            );
        }
    }

    public record Create(
            Long restaurantId,
            boolean duplicated
    ) {
        public static Create from(RestaurantAdminResult.Create result) {
            return new Create(result.restaurantId(), result.duplicated());
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
            RestaurantLocationResponse location,
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            List<String> aiMateSummaryContents,
            TimeSlot timeSlot,
            String teamRecommendationTitle,
            String teamRecommendationReason,
            Boolean isDisplay,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(RestaurantAdminResult.Detail result) {
            return new Detail(
                    result.id(),
                    result.externalId(),
                    result.categoryId(),
                    result.largeCategory(),
                    result.mediumCategory(),
                    result.name(),
                    result.address(),
                    result.rating(),
                    result.imageUrl(),
                    result.mapUrl(),
                    result.representativeReview(),
                    result.description(),
                    result.region(),
                    from(result.location()),
                    result.reviewCount(),
                    result.blogReviewCount(),
                    result.representMenu(),
                    result.representMenuPrice(),
                    result.priceLevel(),
                    result.aiMateSummaryTitle(),
                    result.aiMateSummaryContents(),
                    result.timeSlot(),
                    result.teamRecommendationTitle(),
                    result.teamRecommendationReason(),
                    result.isDisplay(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }

        private static RestaurantLocationResponse from(GeoJson.Point location) {
            if (location == null) {
                return null;
            }
            return new RestaurantLocationResponse(location.getCoordinates());
        }
    }

    public record RestaurantLocationResponse(List<Double> coordinates) {
    }
}
