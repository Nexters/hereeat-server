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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
                ),
                // saveOrRevive가 삭제 여부와 무관하게 external_id를 조회한다.
                // uk_restaurant_external_id_active는 partial index라 이 조회에 못 쓰인다.
                @Index(
                        name = "idx_restaurant_external_id",
                        columnList = "external_id",
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

    // uniqueness는 partial unique index(uk_restaurant_external_id_active, deleted_at IS NULL)가 담당한다.
    // unique = true를 두면 ddl-auto: update가 매 부팅마다 테이블 전체 unique 제약을 다시 붙인다.
    @Column(nullable = false)
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
    @Column(name = "station", length = 30)
    private String station;

    // 추천 시간대 (신규 필드)
    @Column(name = "time_slot", columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private TimeSlot timeSlot;

    // 휴무일
    @Column(name = "off_days", columnDefinition = "TEXT")
    private String offDays;  // JSON 문자열

    @Column(name = "off_days_updated_at")
    private LocalDateTime offDaysUpdatedAt;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "team_recommendation_title", length = 50)
    private String teamRecommendationTitle;

    @Column(name = "team_recommendation_reason", columnDefinition = "TEXT")
    private String teamRecommendationReason;

    @Column(name = "is_display", nullable = false)
    private Boolean isDisplay;

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
            String station,
            // 추천 시간대
            TimeSlot timeSlot,
            // 휴무일
            String offDays,
            LocalDateTime offDaysUpdatedAt,
            String phoneNumber,
            String teamRecommendationTitle,
            String teamRecommendationReason,
            Boolean isDisplay) {
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
        this.station = station;
        this.timeSlot = timeSlot;
        this.offDays = offDays;
        this.offDaysUpdatedAt = offDaysUpdatedAt;
        this.phoneNumber = phoneNumber;
        this.teamRecommendationTitle = teamRecommendationTitle;
        this.teamRecommendationReason = teamRecommendationReason;
        // null 인 경우 NOT NULL 컬럼 제약을 위반하지 않도록 기본값 true로 보정
        this.isDisplay = isDisplay != null ? isDisplay : Boolean.TRUE;
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
                // 휴무일
                .offDays(createRestaurant.offDays())
                .offDaysUpdatedAt(createRestaurant.offDays() != null ? LocalDateTime.now() : null)
                .phoneNumber(createRestaurant.phoneNumber())
                .teamRecommendationTitle(createRestaurant.teamRecommendationTitle())
                .teamRecommendationReason(createRestaurant.teamRecommendationReason())
                .isDisplay(createRestaurant.isDisplay())
                .build();
    }

    /**
     * 소프트 삭제된(deleted_at IS NOT NULL) row를 새 CreateRestaurant 데이터로 완전히
     * 덮어쓰고 되살린다. external_id가 같은 카카오 장소가 재수집될 때, 죽은 row가
     * external_id를 계속 점유하고 있어 새 row를 insert할 수 없는 문제를 우회한다.
     */
    public void applyRevive(CreateRestaurant createRestaurant, Long regionId) {
        this.externalId = createRestaurant.externalId();
        this.categoryId = createRestaurant.categoryId();
        this.name = createRestaurant.name();
        this.address = createRestaurant.address();
        this.rating = createRestaurant.rating();
        this.imageUrl = createRestaurant.imageUrl();
        this.mapUrl = createRestaurant.mapUrl();
        this.representativeReview = createRestaurant.representativeReview();
        this.description = createRestaurant.description();
        this.regionId = regionId;
        this.location = createRestaurant.location() != null
                ? toJtsPoint(createRestaurant.location())
                : null;
        this.reviewCount = createRestaurant.reviewCount();
        this.blogReviewCount = createRestaurant.blogReviewCount();
        this.representMenu = createRestaurant.representMenu();
        this.representMenuPrice = createRestaurant.representMenuPrice();
        this.priceLevel = createRestaurant.priceLevel();
        this.aiMateSummaryTitle = createRestaurant.aiMateSummaryTitle();
        this.aiMateSummaryContents = createRestaurant.aiMateSummaryContents();
        this.timeSlot = createRestaurant.timeSlot();
        this.offDays = createRestaurant.offDays();
        this.offDaysUpdatedAt = createRestaurant.offDays() != null ? LocalDateTime.now() : null;
        this.phoneNumber = createRestaurant.phoneNumber();
        this.teamRecommendationTitle = createRestaurant.teamRecommendationTitle();
        this.teamRecommendationReason = createRestaurant.teamRecommendationReason();
        this.isDisplay = createRestaurant.isDisplay() != null ? createRestaurant.isDisplay() : Boolean.TRUE;
        restore();
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
                entity.getUpdatedAt(),
                // 휴무일
                parseOffDays(entity.getOffDays()),
                entity.getPhoneNumber(),
                entity.getTeamRecommendationTitle(),
                entity.getTeamRecommendationReason(),
                entity.getIsDisplay()
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

    private static List<LocalDate> parseOffDays(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> dateStrings = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return dateStrings.stream().map(LocalDate::parse).toList();
        } catch (Exception e) {
            log.warn("Failed to parse offDays: {}", json);
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
        if (patch.station() != null && !patch.station().isBlank()) {
            this.station = patch.station();
        }
        if (patch.timeSlot() != null) {
            this.timeSlot = patch.timeSlot();
        }
        if (patch.categoryId() != null) {
            this.categoryId = patch.categoryId();
        }
        if (patch.offDays() != null) {
            this.offDays = patch.offDays();
            this.offDaysUpdatedAt = LocalDateTime.now();
        }
        if (patch.phoneNumber() != null && !patch.phoneNumber().isBlank()) {
            this.phoneNumber = patch.phoneNumber();
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
        if (command.teamRecommendationTitle() != null) {
            this.teamRecommendationTitle = command.teamRecommendationTitle();
        }
        if (command.teamRecommendationReason() != null) {
            this.teamRecommendationReason = command.teamRecommendationReason();
        }
        if (command.isDisplay() != null) {
            this.isDisplay = command.isDisplay();
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
