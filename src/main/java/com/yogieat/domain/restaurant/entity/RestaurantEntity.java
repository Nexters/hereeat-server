package com.yogieat.domain.restaurant.entity;

import com.yogieat.domain.common.GeoConverter;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.common.Place;
import com.yogieat.domain.restaurant.domain.CreateRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Getter
@Entity
@Table(
    name = "t_restaurant",
    indexes = {
        @Index(
            name = "idx_restaurant_place",
            columnList = "place",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_name_address",
            columnList = "name, address",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_category_id",
            columnList = "category_id",
            unique = false
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantEntity extends BaseEntity {
    private String name;
    private String address;
    private Double rating;
    private String imageUrl;
    private String mapUrl;
    @Column(columnDefinition = "TEXT")
    private String representativeReview; // 대표 리뷰 1건
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "VARCHAR(30)")
    @Enumerated(EnumType.STRING)
    private Place place;
    private Point location; // 위도, 경도

    @Column(unique = true, nullable = false)
    private String externalId; // Kakao Place ID

    // 매핑 필드
    private Long categoryId; // nullable

    @Builder(access = AccessLevel.PRIVATE)
    private RestaurantEntity(
            String externalId,
            Long categoryId,
            String name,
            String address,
            Double rating,
            String imageUrl,
            String mapUrl,
            String representativeReview,
            String description,
            Place place,
            Point location) {
        this.externalId = externalId;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.mapUrl = mapUrl;
        this.categoryId = categoryId;
        this.representativeReview = representativeReview;
        this.description = description;
        this.place = place;
        this.location = location;
    }

    /**
     * Static factory method to create RestaurantEntity from CreateRestaurant
     * Encapsulates builder usage and prevents external access to builder
     *
     * @param createRestaurant Domain object for creating restaurant
     * @param geoConverter Converter to transform GeoJson.Point to JTS Point
     * @return RestaurantEntity instance
     */
    public static RestaurantEntity from(CreateRestaurant createRestaurant, GeoConverter geoConverter) {
        return builder()
                .externalId(createRestaurant.externalId())
                .categoryId(createRestaurant.categoryId())
                .name(createRestaurant.name())
                .address(createRestaurant.address())
                .rating(createRestaurant.rating())
                .imageUrl(createRestaurant.imageUrl())
                .mapUrl(createRestaurant.mapUrl())
                .representativeReview(createRestaurant.representativeReview())
                .description(createRestaurant.description())
                .place(createRestaurant.place())
                .location(
                        createRestaurant.location() != null
                                ? geoConverter.geoJsonPointToJtsPoint(createRestaurant.location())
                                : null)
                .build();
    }

    public static Restaurant toDomain(RestaurantEntity entity) {
        return new Restaurant(
                entity.getId(),
                entity.getExternalId(),
                entity.getCategoryId(),
                entity.getName(),
                entity.getAddress(),
                entity.getRating(),
                entity.getImageUrl(),
                entity.getMapUrl(),
                entity.getRepresentativeReview(),
                entity.getDescription(),
                entity.getPlace(),
                entity.location != null
                    ? new GeoJson.Point(List.of(entity.location.getX(), entity.location.getY()))
                    : null
        );
    }
}
