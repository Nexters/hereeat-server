package com.yogieat.datasource.db.core.restaurant.reservation;

import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantReservationJpaRepository extends JpaRepository<RestaurantReservationEntity, Long> {
    List<RestaurantReservationEntity> findAllByRestaurantIdAndDeletedAtIsNullOrderByIdAsc(Long restaurantId);
    Optional<RestaurantReservationEntity> findByRestaurantIdAndProviderAndDeletedAtIsNull(
            Long restaurantId,
            ReservationProvider provider
    );
}
