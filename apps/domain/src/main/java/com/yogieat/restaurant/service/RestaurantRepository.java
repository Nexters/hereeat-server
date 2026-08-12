package com.yogieat.restaurant.service;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.result.RestaurantDetailResult;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatch;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RestaurantRepository {
    boolean existsByExternalId(String externalId);
    boolean existsByNameAndAddress(String name, String address);
    Restaurant save(CreateRestaurant createRestaurant);
    Restaurant save(CreateRestaurant createRestaurant, Long regionId);
    /**
     * external_id가 소프트 삭제된 row로 이미 존재하면 그 row를 되살려 덮어쓰고,
     * 그렇지 않으면 새로 insert한다. t_restaurant.external_id의 unique 제약이
     * deleted_at을 고려하지 않기 때문에, 재수집 시 죽은 row와의 충돌을 피하려면
     * 항상 save 대신 이 메서드를 사용해야 한다.
     */
    default Restaurant saveOrRevive(CreateRestaurant createRestaurant) {
        return saveOrRevive(createRestaurant, null);
    }
    Restaurant saveOrRevive(CreateRestaurant createRestaurant, Long regionId);
    List<Restaurant> findAll();
    List<Restaurant> findByRegion(Region region);
    List<Restaurant> findByRegionId(Long regionId);
    List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot
    );
    default List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot,
            Collection<Long> excludedRestaurantIds
    ) {
        return findRecommendationCandidates(region, categoryIds, gatheringTimeSlot, excludedRestaurantIds, null);
    }

    default List<Restaurant> findRecommendationCandidates(
            Region region,
            Collection<Long> categoryIds,
            TimeSlot gatheringTimeSlot,
            Collection<Long> excludedRestaurantIds,
            LocalDate scheduledDate
    ) {
        return findRecommendationCandidates(region, categoryIds, gatheringTimeSlot, excludedRestaurantIds);
    }
    Optional<Restaurant> findById(Long id);
    List<Restaurant> findByIds(List<Long> ids);
    long countByRegion(Region region);  // 지역별 맛집 수 조회 (신규)
    Optional<Restaurant> findByExternalId(String externalId);
    List<Long> findActiveRestaurantIdsAfter(Long lastId, int limit);
    long countActiveRestaurants();
    Restaurant applyAdminPatch(Long restaurantId, RestaurantCommand.Patch command);
    List<RestaurantAdminListItemResult> findPageRestaurants(
            RestaurantAdminListCriteria criteria,
            int page,
            int size
    );
    long countAdminRestaurantList(
            RestaurantAdminListCriteria criteria
    );
    Optional<RestaurantAdminResult.Detail> findAdminRestaurantDetailById(Long restaurantId);
    Optional<RestaurantDetailResult> findRestaurantDetailById(Long restaurantId);
    void deleteBy(Long restaurantId);
    List<RestaurantSyncTarget> findSyncTargetsByIds(List<Long> ids);
    void batchApplySyncPatch(List<RestaurantSyncPatchCommand> commands);
    void batchDeleteByIds(List<Long> restaurantIds);
    void applySyncPatch(Long restaurantId, RestaurantSyncPatch patch);

}
