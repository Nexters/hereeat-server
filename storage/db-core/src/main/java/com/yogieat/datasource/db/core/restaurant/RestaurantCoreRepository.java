package com.yogieat.datasource.db.core.restaurant;

import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RestaurantCoreRepository implements RestaurantRepository {

    private final RestaurantJpaRepository restaurantJpaRepository;
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
        // Convert CreateRestaurant to entity using static factory method
        RestaurantEntity entity = RestaurantEntity.from(createRestaurant);

        // Save and convert back to domain
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
        return restaurantJpaRepository.findActiveIdsAfter(lastId, limit);
    }

    @Override
    public long countActiveRestaurants() {
        return restaurantJpaRepository.countActiveRestaurants();
    }

    @Override
    public List<RestaurantSyncTarget> findSyncTargetsByIds(List<Long> ids) {
        return restaurantJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(entity -> new RestaurantSyncTarget(
                        entity.getId(),
                        entity.getName(),
                        entity.getRegion(),
                        entity.getExternalId()
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
                )
                .toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batchParameters);
    }

    @Override
    @Transactional
    public void batchSoftDeleteByIds(List<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return;
        }

        String sql = """
                update t_restaurant
                set deleted_at = coalesce(deleted_at, now()),
                    updated_at = now()
                where id in (:restaurantIds)
                  and deleted_at is null
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("restaurantIds", restaurantIds);

        namedParameterJdbcTemplate.update(sql, params);
    }

    @Override
    @Transactional
    public void applySyncPatch(Long restaurantId, RestaurantSyncPatch patch) {
        RestaurantEntity entity = restaurantJpaRepository.findById(restaurantId)
                .orElseThrow();
        entity.applySyncPatch(patch);
    }
}
