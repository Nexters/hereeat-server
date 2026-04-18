package com.yogieat.datasource.db.core.restaurant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@Slf4j
@Getter
@Entity
@Table(
    name = "t_restaurant",
    indexes = {
        @Index(
            name = "idx_restaurant_name_address",
            columnList = "name, address",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_category_id",
            columnList = "category_id",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_deleted_at",
            columnList = "deleted_at",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_deleted_at_id",
            columnList = "deleted_at, id",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_region_id",
            columnList = "region_id",
            unique = false
        ),
        @Index(
            name = "idx_restaurant_region_id_deleted_at",
            columnList = "region_id, deleted_at",
            unique = false
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantEntity extends BaseEntity {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String name;
    private String address;
    private Double rating;
    private String imageUrl;
    private String mapUrl;
    @Column(columnDefinition = "TEXT")
    private String representativeReview; // 대표 리뷰 1건
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "region_id")
    private Long regionId;
    private Point location; // 위도, 경도

    @Column(unique = true, nullable = false)
    private String externalId; // Kakao Place ID

    // 매핑 필드
    private Long categoryId; // nullable

    // 추천 근거 데이터 (신규 필드)
    private Integer reviewCount;
    private Integer blogReviewCount;
    private String representMenu;
    private Integer representMenuPrice;
    @Column(columnDefinition = "VARCHAR(10)")
    private String priceLevel;
    private String aiMateSummaryTitle;
    @Column(columnDefinition = "TEXT")
    private String aiMateSummaryContents;  // JSON 문자열

    // 추천 시간대 (신규 필드)
    @Column(name = "time_slot", columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private TimeSlot timeSlot;

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
            Long regionId,
            Point location,
            // 추천 근거 데이터
            Integer reviewCount,
            Integer blogReviewCount,
            String representMenu,
            Integer representMenuPrice,
            String priceLevel,
            String aiMateSummaryTitle,
            String aiMateSummaryContents,
            // 추천 시간대
            TimeSlot timeSlot) {
        this.externalId = externalId;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.mapUrl = mapUrl;
        this.categoryId = categoryId;
        this.representativeReview = representativeReview;
        this.description = description;
        this.regionId = regionId;
        this.location = location;
        this.reviewCount = reviewCount;
        this.blogReviewCount = blogReviewCount;
        this.representMenu = representMenu;
        this.representMenuPrice = representMenuPrice;
        this.priceLevel = priceLevel;
        this.aiMateSummaryTitle = aiMateSummaryTitle;
        this.aiMateSummaryContents = aiMateSummaryContents;
        this.timeSlot = timeSlot;
    }

    /**
     * Static factory method to create RestaurantEntity from CreateRestaurant
     * Encapsulates builder usage and prevents external access to builder
     *
     * @param createRestaurant Domain object for creating restaurant
     * @return RestaurantEntity instance
     */
    public static RestaurantEntity from(CreateRestaurant createRestaurant) {
        return from(createRestaurant, null);
    }

    public static RestaurantEntity from(CreateRestaurant createRestaurant, Long regionId) {
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
                .regionId(regionId)
                .location(
                        createRestaurant.location() != null
                                ? toJtsPoint(createRestaurant.location())
                                : null)
                // 추천 근거 데이터
                .reviewCount(createRestaurant.reviewCount())
                .blogReviewCount(createRestaurant.blogReviewCount())
                .representMenu(createRestaurant.representMenu())
                .representMenuPrice(createRestaurant.representMenuPrice())
                .priceLevel(createRestaurant.priceLevel())
                .aiMateSummaryTitle(createRestaurant.aiMateSummaryTitle())
                .aiMateSummaryContents(createRestaurant.aiMateSummaryContents())
                // 추천 시간대
                .timeSlot(createRestaurant.timeSlot())
                .build();
    }

    public static Restaurant toDomain(RestaurantEntity entity, Region region) {
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
                region,
                entity.location != null
                    ? new GeoJson.Point(List.of(entity.location.getX(), entity.location.getY()))
                    : null,
                // 추천 근거 데이터
                entity.getReviewCount(),
                entity.getBlogReviewCount(),
                entity.getRepresentMenu(),
                entity.getRepresentMenuPrice(),
                entity.getPriceLevel(),
                entity.getAiMateSummaryTitle(),
                parseAiMateSummaryContents(entity.getAiMateSummaryContents()),
                // 추천 시간대
                entity.getTimeSlot(),
                // 추천 알고리즘용 시간 데이터
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static Point toJtsPoint(GeoJson.Point point) {
        List<Double> coordinates = point.getCoordinates();
        return GEOMETRY_FACTORY.createPoint(new Coordinate(coordinates.getFirst(), coordinates.get(1)));
    }

    private static List<String> parseAiMateSummaryContents(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse aiMateSummaryContents: {}", json);
            return Collections.emptyList();
        }
    }

    public void applySyncPatch(RestaurantSyncPatch patch) {
        if (patch.externalId() != null && !patch.externalId().isBlank()) {
            this.externalId = patch.externalId();
        }
        if (patch.name() != null && !patch.name().isBlank()) {
            this.name = patch.name();
        }
        if (patch.mapUrl() != null && !patch.mapUrl().isBlank()) {
            this.mapUrl = patch.mapUrl();
        }
        if (patch.location() != null) {
            this.location = toJtsPoint(patch.location());
        }
        if (patch.rating() != null) {
            this.rating = patch.rating();
        }
        if (patch.imageUrl() != null && !patch.imageUrl().isBlank()) {
            this.imageUrl = patch.imageUrl();
        }
        if (patch.representativeReview() != null && !patch.representativeReview().isBlank()) {
            this.representativeReview = patch.representativeReview();
        }
        if (patch.reviewCount() != null) {
            this.reviewCount = patch.reviewCount();
        }
        if (patch.blogReviewCount() != null) {
            this.blogReviewCount = patch.blogReviewCount();
        }
        if (patch.representMenu() != null && !patch.representMenu().isBlank()) {
            this.representMenu = patch.representMenu();
        }
        if (patch.representMenuPrice() != null) {
            this.representMenuPrice = patch.representMenuPrice();
        }
        if (patch.priceLevel() != null && !patch.priceLevel().isBlank()) {
            this.priceLevel = patch.priceLevel();
        }
        if (patch.aiMateSummaryTitle() != null && !patch.aiMateSummaryTitle().isBlank()) {
            this.aiMateSummaryTitle = patch.aiMateSummaryTitle();
        }
        if (patch.aiMateSummaryContents() != null && !patch.aiMateSummaryContents().isBlank()) {
            this.aiMateSummaryContents = patch.aiMateSummaryContents();
        }
        if (patch.timeSlot() != null) {
            this.timeSlot = patch.timeSlot();
        }
        if (patch.categoryId() != null) {
            this.categoryId = patch.categoryId();
        }
    }

    public void applyAdminPatch(RestaurantCommand.Patch command, Long regionId) {
        if (command == null) {
            return;
        }
        if (command.externalId() != null && !command.externalId().isBlank()) {
            this.externalId = command.externalId();
        }
        if (command.name() != null && !command.name().isBlank()) {
            this.name = command.name();
        }
        if (command.address() != null && !command.address().isBlank()) {
            this.address = command.address();
        }
        if (command.categoryId() != null) {
            this.categoryId = command.categoryId();
        }
        if (command.region() != null) {
            this.regionId = regionId;
        }
        if (command.location() != null) {
            this.location = toJtsPoint(command.location());
        }
        if (command.rating() != null) {
            this.rating = command.rating();
        }
        if (command.imageUrl() != null && !command.imageUrl().isBlank()) {
            this.imageUrl = command.imageUrl();
        }
        if (command.mapUrl() != null && !command.mapUrl().isBlank()) {
            this.mapUrl = command.mapUrl();
        }
        if (command.representativeReview() != null && !command.representativeReview().isBlank()) {
            this.representativeReview = command.representativeReview();
        }
        if (command.description() != null && !command.description().isBlank()) {
            this.description = command.description();
        }
        if (command.reviewCount() != null) {
            this.reviewCount = command.reviewCount();
        }
        if (command.blogReviewCount() != null) {
            this.blogReviewCount = command.blogReviewCount();
        }
        if (command.representMenu() != null && !command.representMenu().isBlank()) {
            this.representMenu = command.representMenu();
        }
        if (command.representMenuPrice() != null) {
            this.representMenuPrice = command.representMenuPrice();
        }
        if (command.priceLevel() != null && !command.priceLevel().isBlank()) {
            this.priceLevel = command.priceLevel();
        }
        if (command.aiMateSummaryTitle() != null && !command.aiMateSummaryTitle().isBlank()) {
            this.aiMateSummaryTitle = command.aiMateSummaryTitle();
        }
        if (command.aiMateSummaryContents() != null && !command.aiMateSummaryContents().isEmpty()) {
            this.aiMateSummaryContents = toJsonString(command.aiMateSummaryContents());
        }
        if (command.timeSlot() != null) {
            this.timeSlot = command.timeSlot();
        }
    }

    private static String toJsonString(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert aiMateSummaryContents: {}", values, e);
            return null;
        }
    }
}
