package com.yogieat.domain.restaurant.entity;

import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Getter
@Entity
@Table(name = "t_restaurant")
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
    private Point location; // 위도, 경도

    @Column(unique = true, nullable = false)
    private String externalId; // Kakao Place ID

    // 매핑 필드
    private Long categoryId; // nullable

    @Builder(access = AccessLevel.PUBLIC)
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
        this.location = location;
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
                entity.location != null
                    ? new GeoJson.Point(List.of(entity.location.getX(), entity.location.getY()))
                    : null
        );
    }
}
