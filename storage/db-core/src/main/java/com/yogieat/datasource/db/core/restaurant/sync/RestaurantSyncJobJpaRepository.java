package com.yogieat.datasource.db.core.restaurant.sync;

import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RestaurantSyncJobJpaRepository extends JpaRepository<RestaurantSyncJobEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            with candidate as (
                select id
                from t_restaurant_sync_job
                where status = 'PENDING'
                order by created_at asc
                for update skip locked
                limit 1
            )
            update t_restaurant_sync_job job
            set status = 'RUNNING',
                started_at = now(),
                error_summary = null,
                updated_at = now()
            from candidate
            where job.id = candidate.id
            returning job.id
            """, nativeQuery = true)
    List<Long> claimNextPendingJobIds();

    boolean existsByScopeAndStatus(RestaurantSyncScope scope, RestaurantSyncJobStatus status);

    boolean existsByTargetRestaurantIdAndStatus(Long targetRestaurantId, RestaurantSyncJobStatus status);
}
