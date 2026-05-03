package com.yogieat.datasource.db.core.restaurant;

import static com.yogieat.datasource.db.core.category.QCategoryEntity.*;
import static com.yogieat.datasource.db.core.region.QRegionEntity.*;
import static com.yogieat.datasource.db.core.restaurant.QRestaurantEntity.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.result.RestaurantDetailResult;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RestaurantCoreRepository implements RestaurantRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final RestaurantJpaRepository restaurantJpaRepository;
    private final RegionJpaRepository regionJpaRepository;
    private final JPAQueryFactory jpaQueryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public boolean existsByExternalId(String externalId) {
        return restaurantJpaRepository.existsByExternalIdAndDeletedAtIsNull(externalId);
    }

    @Override
    public boolean existsByNameAndAddress(String name, String address) {
        return restaurantJpaRepository.existsByNameAndAddressAndDeletedAtIsNull(name, address);
    }

    @Override
    public Restaurant save(CreateRestaurant createRestaurant) {
        return save(createRestaurant, null);
    }

    @Override
    public Restaurant save(CreateRestaurant createRestaurant, Long regionId) {
        RestaurantEntity entity = RestaurantEntity.from(
                createRestaurant,
                regionId != null ? regionId : resolveRegionId(createRestaurant.region())
        );
        RestaurantEntity savedEntity = restaurantJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<Restaurant> findAll() {
        return toDomainRestaurants(restaurantJpaRepository.findAllByDeletedAtIsNull());
    }

    @Override
    public List<Restaurant> findByRegion(Region region) {
        List<RestaurantEntity> entities = jpaQueryFactory.selectFrom(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        regionCondition(region)
                )
                .fetch();
        return toDomainRestaurants(entities);
    }

    @Override
    public List<Restaurant> findByRegionId(Long regionId) {
        if (regionId == null) {
            return List.of();
        }

        List<RestaurantEntity> entities = jpaQueryFactory.selectFrom(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        restaurantEntity.regionId.eq(regionId)
                )
                .fetch();
        return toDomainRestaurants(entities);
    }

    @Override
    public List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot
    ) {
        return findRecommendationCandidates(region, categoryIds, gatheringTimeSlot, List.of(), null);
    }

    @Override
    public List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot,
            Collection<Long> excludedRestaurantIds
    ) {
        return findRecommendationCandidates(region, categoryIds, gatheringTimeSlot, excludedRestaurantIds, null);
    }

    @Override
    public List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot,
            Collection<Long> excludedRestaurantIds,
            LocalDate scheduledDate
    ) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        List<Tuple> tuples = jpaQueryFactory
                .select(
                        restaurantEntity.id,
                        restaurantEntity.categoryId,
                        restaurantEntity.name,
                        restaurantEntity.rating,
                        restaurantEntity.regionId,
                        restaurantEntity.location,
                        restaurantEntity.reviewCount,
                        restaurantEntity.blogReviewCount,
                        restaurantEntity.aiMateSummaryTitle,
                        restaurantEntity.aiMateSummaryContents,
                        restaurantEntity.timeSlot,
                        restaurantEntity.createdAt,
                        restaurantEntity.updatedAt,
                        restaurantEntity.offDays
                )
                .from(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        regionCondition(region),
                        restaurantEntity.categoryId.in(categoryIds),
                        recommendationTimeSlotCondition(gatheringTimeSlot),
                        excludedRestaurantIdsCondition(excludedRestaurantIds),
                        offDaysNotContainsCondition(scheduledDate)
                )
                .fetch();
        Map<Long, Region> regionMap = resolveRegionMap(
                tuples.stream()
                        .map(tuple -> tuple.get(restaurantEntity.regionId))
                        .toList()
        );

        return tuples
                .stream()
                .map(tuple -> toRecommendationCandidate(tuple, regionMap))
                .toList();
    }

    @Override
    public Optional<Restaurant> findById(Long id) {
        return restaurantJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toDomain);
    }

    @Override
    public List<Restaurant> findByIds(List<Long> ids) {
        return toDomainRestaurants(restaurantJpaRepository.findByIdInAndDeletedAtIsNull(ids));
    }

    @Override
    @Transactional
    public Restaurant applyAdminPatch(Long restaurantId, RestaurantCommand.Patch command) {
        RestaurantEntity entity = restaurantJpaRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
        entity.applyAdminPatch(
                command,
                command.region() != null ? resolveRegionId(command.region()) : null
        );
        return toDomain(entity);
    }

    @Override
    public void deleteBy(Long restaurantId) {
        RestaurantEntity entity = restaurantJpaRepository.findById(restaurantId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
        restaurantJpaRepository.delete(entity);
    }

    private Long resolveRegionId(Region region) {
        if (region == null) {
            return null;
        }
        return regionJpaRepository.findIdByCode(region.name()).orElse(null);
    }

    @Override
    public long countByRegion(Region region) {
        Long count = jpaQueryFactory.select(restaurantEntity.count())
                .from(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        regionCondition(region)
                )
                .fetchOne();

        return count == null ? 0L : count;
    }

    @Override
    public Optional<Restaurant> findByExternalId(String externalId) {
        return restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull(externalId)
                .map(this::toDomain);
    }

    @Override
    public List<Long> findActiveRestaurantIdsAfter(Long lastId, int limit) {
        BooleanExpression idCondition = lastId == null ? null : restaurantEntity.id.gt(lastId);

        return jpaQueryFactory.select(restaurantEntity.id)
                .from(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        idCondition
                )
                .orderBy(restaurantEntity.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countActiveRestaurants() {
        return restaurantJpaRepository.countByDeletedAtIsNull();
    }

    @Override
    public List<RestaurantAdminListItemResult> findPageRestaurants(
            RestaurantAdminListCriteria criteria,
            int page,
            int size
    ) {
        List<Tuple> tuples = createAdminRestaurantTupleQuery(criteria)
                .orderBy(restaurantEntity.updatedAt.desc(), restaurantEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        Map<Long, Region> regionMap = resolveRegionMap(
                tuples.stream()
                        .map(tuple -> tuple.get(restaurantEntity))
                        .map(RestaurantEntity::getRegionId)
                        .toList()
        );

        return tuples
                .stream()
                .map(tuple -> toAdminListItem(tuple, regionMap))
                .toList();
    }

    @Override
    public long countAdminRestaurantList(
            RestaurantAdminListCriteria criteria
    ) {
        Long total = jpaQueryFactory.select(restaurantEntity.count())
                .from(restaurantEntity)
                .leftJoin(categoryEntity)
                .on(restaurantEntity.categoryId.eq(categoryEntity.id))
                .where(buildAdminRestaurantConditions(criteria))
                .fetchOne();

        return total == null ? 0L : total;
    }

    @Override
    public Optional<RestaurantAdminResult.Detail> findAdminRestaurantDetailById(Long restaurantId) {
        Tuple tuple = createAdminRestaurantTupleQuery(RestaurantAdminListCriteria.of(null, null, null, null))
                .where(
                        restaurantEntity.id.eq(restaurantId)
                )
                .fetchOne();

        if (tuple == null) {
            return Optional.empty();
        }

        RestaurantEntity entity = tuple.get(restaurantEntity);
        if (entity == null) {
            return Optional.empty();
        }

        return Optional.of(
                RestaurantAdminResult.Detail.of(
                        entity.getId(),
                        entity.getExternalId(),
                        entity.getCategoryId(),
                        tuple.get(categoryEntity.largeCategory),
                        tuple.get(categoryEntity.mediumCategory),
                        entity.getName(),
                        entity.getAddress(),
                        entity.getRating(),
                        entity.getImageUrl(),
                        entity.getMapUrl(),
                        entity.getRepresentativeReview(),
                        entity.getDescription(),
                        toRegion(tuple.get(regionEntity.code)),
                        toGeoJsonPoint(entity.getLocation()),
                        entity.getReviewCount(),
                        entity.getBlogReviewCount(),
                        entity.getRepresentMenu(),
                        entity.getRepresentMenuPrice(),
                        entity.getPriceLevel(),
                        entity.getAiMateSummaryTitle(),
                        parseAiMateSummaryContents(entity.getAiMateSummaryContents()),
                        entity.getTimeSlot(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                )
        );
    }

    @Override
    public Optional<RestaurantDetailResult> findRestaurantDetailById(Long restaurantId) {
        Tuple tuple = jpaQueryFactory
                .select(restaurantEntity, categoryEntity.largeCategory, regionEntity.code)
                .from(restaurantEntity)
                .leftJoin(categoryEntity)
                .on(restaurantEntity.categoryId.eq(categoryEntity.id))
                .leftJoin(regionEntity)
                .on(restaurantEntity.regionId.eq(regionEntity.id))
                .where(restaurantEntity.deletedAt.isNull())
                .where(restaurantEntity.id.eq(restaurantId))
                .fetchOne();

        if (tuple == null) {
            return Optional.empty();
        }

        RestaurantEntity entity = tuple.get(restaurantEntity);
        if (entity == null) {
            return Optional.empty();
        }

        return Optional.of(RestaurantDetailResult.of(
                entity.getId(),
                entity.getName(),
                entity.getStation(),
                entity.getAddress(),
                toRegion(tuple.get(regionEntity.code)),
                tuple.get(categoryEntity.largeCategory),
                entity.getRating(),
                entity.getImageUrl(),
                entity.getMapUrl(),
                entity.getDescription(),
                entity.getPriceLevel(),
                entity.getRepresentMenu(),
                entity.getRepresentMenuPrice(),
                entity.getRepresentativeReview(),
                entity.getReviewCount(),
                entity.getAiMateSummaryTitle(),
                parseAiMateSummaryContents(entity.getAiMateSummaryContents())
        ));
    }

    private JPAQuery<Tuple> createAdminRestaurantTupleQuery(
            RestaurantAdminListCriteria criteria
    ) {
        return jpaQueryFactory
                .select(restaurantEntity, categoryEntity.largeCategory, categoryEntity.mediumCategory)
                .from(restaurantEntity)
                .leftJoin(categoryEntity)
                .on(restaurantEntity.categoryId.eq(categoryEntity.id))
                .where(buildAdminRestaurantConditions(criteria));
    }

    private RestaurantAdminListItemResult toAdminListItem(Tuple tuple, Map<Long, Region> regionMap) {
        RestaurantEntity entity = tuple.get(restaurantEntity);
        return new RestaurantAdminListItemResult(
                entity.getId(),
                entity.getName(),
                entity.getCategoryId(),
                tuple.get(categoryEntity.largeCategory),
                tuple.get(categoryEntity.mediumCategory),
                entity.getRating(),
                entity.getImageUrl(),
                regionMap.get(entity.getRegionId()),
                entity.getUpdatedAt()
        );
    }

    private BooleanExpression[] buildAdminRestaurantConditions(
            RestaurantAdminListCriteria criteria
    ) {
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(restaurantEntity.deletedAt.isNull());

        if (criteria.region() != null) {
            conditions.add(regionCondition(criteria.region()));
        }

        if (criteria.categoryId() != null) {
            conditions.add(restaurantEntity.categoryId.eq(criteria.categoryId()));
        }

        if (criteria.largeCategory() != null) {
            conditions.add(categoryEntity.largeCategory.eq(criteria.largeCategory()));
        }

        BooleanExpression keywordCondition = createKeywordCondition(criteria.keyword());
        if (keywordCondition != null) {
            conditions.add(keywordCondition);
        }

        return conditions.toArray(BooleanExpression[]::new);
    }

    private BooleanExpression createKeywordCondition(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String trimmedKeyword = keyword.strip();
        BooleanExpression keywordCondition = restaurantEntity.name.containsIgnoreCase(trimmedKeyword)
                .or(restaurantEntity.address.containsIgnoreCase(trimmedKeyword))
                .or(restaurantEntity.externalId.containsIgnoreCase(trimmedKeyword));

        if (trimmedKeyword.chars().allMatch(Character::isDigit)) {
            try {
                keywordCondition = keywordCondition.or(restaurantEntity.id.eq(Long.valueOf(trimmedKeyword)));
            } catch (NumberFormatException ignored) {
                // 숫자 키워드지만 Long 범위를 초과한 경우 id 검색은 생략하고 텍스트 검색만 수행
            }
        }

        return keywordCondition;
    }

    private BooleanExpression recommendationTimeSlotCondition(TimeSlot gatheringTimeSlot) {
        if (gatheringTimeSlot == null || gatheringTimeSlot == TimeSlot.BOTH) {
            return null;
        }

        return restaurantEntity.timeSlot.isNull()
                .or(restaurantEntity.timeSlot.eq(TimeSlot.BOTH))
                .or(restaurantEntity.timeSlot.eq(gatheringTimeSlot));
    }

    private BooleanExpression excludedRestaurantIdsCondition(Collection<Long> excludedRestaurantIds) {
        if (excludedRestaurantIds == null || excludedRestaurantIds.isEmpty()) {
            return null;
        }

        return restaurantEntity.id.notIn(excludedRestaurantIds);
    }

    private BooleanExpression offDaysNotContainsCondition(LocalDate scheduledDate) {
        if (scheduledDate == null) {
            return null;
        }
        // off_days는 ["YYYY-MM-DD", ...] 형식의 JSON 텍스트이므로 quoted 날짜 문자열 포함 여부로 판단
        String datePattern = "%\"" + scheduledDate + "\"%";
        return restaurantEntity.offDays.isNull()
                .or(restaurantEntity.offDays.notLike(datePattern));
    }

    private Restaurant toRecommendationCandidate(Tuple tuple, Map<Long, Region> regionMap) {
        Point location = tuple.get(restaurantEntity.location);
        Long regionId = tuple.get(restaurantEntity.regionId);
        Region resolvedRegion = regionMap.get(regionId);

        return new Restaurant(
                tuple.get(restaurantEntity.id),
                null,
                tuple.get(restaurantEntity.categoryId),
                tuple.get(restaurantEntity.name),
                null,
                tuple.get(restaurantEntity.rating),
                null,
                null,
                null,
                null,
                resolvedRegion,
                toGeoJsonPoint(location),
                tuple.get(restaurantEntity.reviewCount),
                tuple.get(restaurantEntity.blogReviewCount),
                null,
                null,
                null,
                tuple.get(restaurantEntity.aiMateSummaryTitle),
                parseAiMateSummaryContents(tuple.get(restaurantEntity.aiMateSummaryContents)),
                tuple.get(restaurantEntity.timeSlot),
                tuple.get(restaurantEntity.createdAt),
                tuple.get(restaurantEntity.updatedAt),
                parseOffDays(tuple.get(restaurantEntity.offDays))
        );
    }

    private GeoJson.Point toGeoJsonPoint(Point location) {
        if (location == null) {
            return null;
        }
        return new GeoJson.Point(List.of(location.getX(), location.getY()));
    }

    private List<String> parseAiMateSummaryContents(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<LocalDate> parseOffDays(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> dateStrings = OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
            return dateStrings.stream().map(LocalDate::parse).toList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<RestaurantSyncTarget> findSyncTargetsByIds(List<Long> ids) {
        List<RestaurantEntity> entities = restaurantJpaRepository.findByIdInAndDeletedAtIsNull(ids);
        Map<Long, RegionMaster> regionMap = resolveRegionMasterMap(
                entities.stream()
                        .map(RestaurantEntity::getRegionId)
                        .toList()
        );

        return entities.stream()
                .map(entity -> {
                    RegionMaster region = regionMap.get(entity.getRegionId());
                    return new RestaurantSyncTarget(
                            entity.getId(),
                            entity.getName(),
                            region == null ? null : region.code(),
                            region == null ? null : region.displayName(),
                            region == null ? null : region.coordinatesStandard(),
                            entity.getExternalId(),
                            entity.getLocation() == null
                                    ? null
                                    : new GeoJson.Point(List.of(entity.getLocation().getX(), entity.getLocation().getY()))
                    );
                })
                .toList();
    }

    private BooleanExpression regionCondition(Region region) {
        if (region == null) {
            return null;
        }

        Long regionId = resolveRegionId(region);
        if (regionId == null) {
            return restaurantEntity.regionId.isNull().and(restaurantEntity.regionId.isNotNull());
        }

        return restaurantEntity.regionId.eq(regionId);
    }

    private Restaurant toDomain(RestaurantEntity entity) {
        return RestaurantEntity.toDomain(entity, resolveRegion(entity.getRegionId()));
    }

    private List<Restaurant> toDomainRestaurants(List<RestaurantEntity> entities) {
        Map<Long, Region> regionMap = resolveRegionMap(
                entities.stream()
                        .map(RestaurantEntity::getRegionId)
                        .toList()
        );

        return entities.stream()
                .map(entity -> RestaurantEntity.toDomain(entity, regionMap.get(entity.getRegionId())))
                .toList();
    }

    private Region resolveRegion(Long regionId) {
        if (regionId == null) {
            return null;
        }

        return regionJpaRepository.findByIdAndDeletedAtIsNull(regionId)
                .map(regionEntity -> toRegion(regionEntity.getCode()))
                .orElse(null);
    }

    private Map<Long, Region> resolveRegionMap(Collection<Long> regionIds) {
        List<Long> distinctRegionIds = regionIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctRegionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Region> regionMap = new HashMap<>();
        regionJpaRepository.findByIdInAndDeletedAtIsNull(distinctRegionIds)
                .forEach(regionEntity -> regionMap.put(regionEntity.getId(), toRegion(regionEntity.getCode())));
        return regionMap;
    }

    private Map<Long, RegionMaster> resolveRegionMasterMap(Collection<Long> regionIds) {
        List<Long> distinctRegionIds = regionIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctRegionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, RegionMaster> regionMap = new HashMap<>();
        regionJpaRepository.findByIdInAndDeletedAtIsNull(distinctRegionIds)
                .forEach(regionEntity -> regionMap.put(regionEntity.getId(), com.yogieat.datasource.db.core.region.RegionEntity.toDomain(regionEntity)));
        return regionMap;
    }

    private Region toRegion(String regionCode) {
        return Region.fromString(regionCode);
    }

    @Override
    @Transactional
    public void batchApplySyncPatch(List<RestaurantSyncPatchCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }

        String sql = """
                update t_restaurant
                set external_id = coalesce(:externalId, external_id),
                    "name" = coalesce(:name, "name"),
                    map_url = coalesce(:mapUrl, map_url),
                    "location" = case
                        when :hasLocation
                            then ST_SetSRID(
                                    ST_Point(
                                            cast(:longitude as double precision),
                                            cast(:latitude as double precision)
                                    ),
                                    4326
                                 )
                        else "location"
                    end,
                    rating = coalesce(:rating, rating),
                    image_url = coalesce(:imageUrl, image_url),
                    representative_review = coalesce(:representativeReview, representative_review),
                    review_count = coalesce(:reviewCount, review_count),
                    blog_review_count = coalesce(:blogReviewCount, blog_review_count),
                    represent_menu = coalesce(:representMenu, represent_menu),
                    represent_menu_price = coalesce(:representMenuPrice, represent_menu_price),
                    price_level = coalesce(:priceLevel, price_level),
                    ai_mate_summary_title = coalesce(:aiMateSummaryTitle, ai_mate_summary_title),
                    ai_mate_summary_contents = coalesce(:aiMateSummaryContents, ai_mate_summary_contents),
                    station = coalesce(:station, station),
                    time_slot = coalesce(:timeSlot, time_slot),
                    category_id = coalesce(:categoryId, category_id),
                    off_days = coalesce(:offDays, off_days),
                    off_days_updated_at = case when :offDays is not null then now() else off_days_updated_at end,
                    updated_at = now()
                where id = :restaurantId
                  and deleted_at is null
                """;

        MapSqlParameterSource[] batchParameters = commands.stream()
                .map(command -> new MapSqlParameterSource()
                        .addValue("restaurantId", command.restaurantId())
                        .addValue("externalId", command.externalId())
                        .addValue("name", command.name())
                        .addValue("mapUrl", command.mapUrl())
                        .addValue("hasLocation",
                                command.longitude() != null && command.latitude() != null,
                                Types.BOOLEAN)
                        .addValue("longitude", command.longitude(), Types.DOUBLE)
                        .addValue("latitude", command.latitude(), Types.DOUBLE)
                        .addValue("rating", command.rating())
                        .addValue("imageUrl", command.imageUrl())
                        .addValue("representativeReview", command.representativeReview())
                        .addValue("reviewCount", command.reviewCount())
                        .addValue("blogReviewCount", command.blogReviewCount())
                        .addValue("representMenu", command.representMenu())
                        .addValue("representMenuPrice", command.representMenuPrice())
                        .addValue("priceLevel", command.priceLevel())
                        .addValue("aiMateSummaryTitle", command.aiMateSummaryTitle())
                        .addValue("aiMateSummaryContents", command.aiMateSummaryContents())
                        .addValue("station", command.station())
                        .addValue("timeSlot", command.timeSlot() != null ? command.timeSlot().name() : null)
                        .addValue("categoryId", command.categoryId())
                        .addValue("offDays", command.offDays())
                )
                .toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batchParameters);
    }

    @Override
    @Transactional
    public void batchDeleteByIds(List<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jpaQueryFactory
                .update(restaurantEntity)
                .set(restaurantEntity.deletedAt, now)
                .set(restaurantEntity.updatedAt, now)
                .where(
                        restaurantEntity.id.in(restaurantIds),
                        restaurantEntity.deletedAt.isNull()
                )
                .execute();
    }

    @Override
    @Transactional
    public void applySyncPatch(Long restaurantId, RestaurantSyncPatch patch) {
        RestaurantEntity entity = restaurantJpaRepository.findById(restaurantId)
                .orElseThrow();
        entity.applySyncPatch(patch);
    }
}
