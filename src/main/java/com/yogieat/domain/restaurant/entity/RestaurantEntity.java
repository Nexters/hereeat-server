package com.yogieat.domain.restaurant.entity;

import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_restaurant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantEntity extends BaseEntity {
    private String name;
    private String address;
    @Column(precision = 3, scale = 2)
    private Double rating;
    private String imageUrl;
    private String mapUrl;
    // ex. largeCategory: 양식, mediumCategory: 이탈리안
    private String largeCategory;
    private String mediumCategory;
    @Column(columnDefinition = "TEXT")
    private String representativeReview; // 대표 리뷰 1건
    @Column(columnDefinition = "TEXT")
    private String description;
    // TODO: 위치정보는 추가할 예정 (위도, 경도)

    @Builder(access = AccessLevel.PRIVATE)
    private RestaurantEntity(
            String name,
            String address,
            Double rating,
            String imageUrl,
            String mapUrl,
            String largeCategory,
            String mediumCategory,
            String representativeReview,
            String description) {
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.mapUrl = mapUrl;
        this.largeCategory = largeCategory;
        this.mediumCategory = mediumCategory;
        this.representativeReview = representativeReview;
        this.description = description;
    }

    public static Restaurant toDomain(RestaurantEntity entity) {
        return new Restaurant(
                entity.getName(),
                entity.getAddress(),
                entity.getRating(),
                entity.getImageUrl(),
                entity.getMapUrl(),
                entity.getLargeCategory(),
                entity.getMediumCategory(),
                entity.getRepresentativeReview(),
                entity.getDescription()
        );
    }
}
