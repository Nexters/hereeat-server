package com.yogieat.datasource.db.core.restaurant;

import static com.yogieat.datasource.db.core.category.QCategoryEntity.*;
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
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
        RestaurantEntity entity = RestaurantEntity.from(
                createRestaurant,
                resolveRegionId(createRestaurant.region())
        );
        RestaurantEntity savedEntity = restaurantJpaRepository.save(entity);
        return RestaurantEntity.toDomain(savedEntity);
    }

    @Override
    public List<Restaurant> findAll() {
        return restaurantJpaRepository.findAllByDeletedAtIsNull().stream()
                .map(RestaurantEntity::toDomain)
                .toList();
    }

    @Override
    public List<Restaurant> findByRegion(Region region) {
        return restaurantJpaRepository.findByRegionAndDeletedAtIsNull(region).stream()
                .map(RestaurantEntity::toDomain)
                .toList();
    }

    @Override
    public List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot
    ) {
        return findRecommendationCandidates(region, categoryIds, gatheringTimeSlot, List.of());
    }

    @Override
    public List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot,
            Collection<Long> excludedRestaurantIds
    ) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }

        return jpaQueryFactory
                .select(
                        restaurantEntity.id,
                        restaurantEntity.categoryId,
                        restaurantEntity.name,
                        restaurantEntity.rating,
                        restaurantEntity.region,
                        restaurantEntity.location,
                        restaurantEntity.reviewCount,
                        restaurantEntity.blogReviewCount,
                        restaurantEntity.aiMateSummaryTitle,
                        restaurantEntity.aiMateSummaryContents,
                        restaurantEntity.timeSlot,
                        restaurantEntity.createdAt,
                        restaurantEntity.updatedAt
                )
                .from(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        restaurantEntity.region.eq(region),
                        restaurantEntity.categoryId.in(categoryIds),
                        recommendationTimeSlotCondition(gatheringTimeSlot),
                        excludedRestaurantIdsCondition(excludedRestaurantIds)
                )
                .fetch()
                .stream()
                .map(this::toRecommendationCandidate)
                .toList();
    }

    @Override
    public Optional<Restaurant> findById(Long id) {
        return restaurantJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(RestaurantEntity::toDomain);
    }

    @Override
    public List<Restaurant> findByIds(List<Long> ids) {
        return restaurantJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(RestaurantEntity::toDomain)
                .toList();
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
        return RestaurantEntity.toDomain(entity);
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
        return restaurantJpaRepository.countByRegionAndDeletedAtIsNull(region);
    }

    @Override
    public Optional<Restaurant> findByExternalId(String externalId) {
        return restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull(externalId)
                .map(RestaurantEntity::toDomain);
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
        return createAdminRestaurantTupleQuery(criteria)
                .orderBy(restaurantEntity.updatedAt.desc(), restaurantEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(this::toAdminListItem)
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

        Restaurant restaurant = RestaurantEntity.toDomain(entity);
        return Optional.of(
                RestaurantAdminResult.Detail.of(
                        restaurant.id(),
                        restaurant.externalId(),
                        restaurant.categoryId(),
                        tuple.get(categoryEntity.largeCategory),
                        tuple.get(categoryEntity.mediumCategory),
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
                )
        );
    }

    private JPAQuery<RestaurantEntity> createAdminRestaurantQuery(
            RestaurantAdminListCriteria criteria
    ) {
        return jpaQueryFactory.selectFrom(restaurantEntity)
                .leftJoin(categoryEntity)
                .on(restaurantEntity.categoryId.eq(categoryEntity.id))
                .where(buildAdminRestaurantConditions(criteria));
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

    private RestaurantAdminListItemResult toAdminListItem(Tuple tuple) {
        RestaurantEntity entity = tuple.get(restaurantEntity);
        return new RestaurantAdminListItemResult(
                entity.getId(),
                entity.getName(),
                entity.getCategoryId(),
                tuple.get(categoryEntity.largeCategory),
                tuple.get(categoryEntity.mediumCategory),
                entity.getRating(),
                entity.getImageUrl(),
                entity.getRegion(),
                entity.getUpdatedAt()
        );
    }

    private BooleanExpression[] buildAdminRestaurantConditions(
            RestaurantAdminListCriteria criteria
    ) {
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(restaurantEntity.deletedAt.isNull());

        if (criteria.region() != null) {
            conditions.add(restaurantEntity.region.eq(criteria.region()));
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

    private Restaurant toRecommendationCandidate(Tuple tuple) {
        Point location = tuple.get(restaurantEntity.location);

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
                tuple.get(restaurantEntity.region),
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
                tuple.get(restaurantEntity.updatedAt)
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

    @Override
    public List<RestaurantSyncTarget> findSyncTargetsByIds(List<Long> ids) {
        return restaurantJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(entity -> new RestaurantSyncTarget(
                        entity.getId(),
                        entity.getName(),
                        entity.getRegion(),
                        entity.getExternalId(),
                        entity.getLocation() == null
                                ? null
                                : new GeoJson.Point(List.of(entity.getLocation().getX(), entity.getLocation().getY()))
                ))
                .toList();
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
                    time_slot = coalesce(:timeSlot, time_slot),
                    category_id = coalesce(:categoryId, category_id),
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
                        .addValue("timeSlot", command.timeSlot() != null ? command.timeSlot().name() : null)
                        .addValue("categoryId", command.categoryId())
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
