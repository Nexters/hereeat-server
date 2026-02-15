package com.yogieat.datasource.db.core.restaurant.sync;

import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantSyncJobJpaRepository extends JpaRepository<RestaurantSyncJobEntity, Long> {
    Optional<RestaurantSyncJobEntity> findTopByStatusOrderByCreatedAtAsc(RestaurantSyncJobStatus status);

    boolean existsByScopeAndStatus(RestaurantSyncScope scope, RestaurantSyncJobStatus status);

    boolean existsByTargetRestaurantIdAndStatus(Long targetRestaurantId, RestaurantSyncJobStatus status);
}
