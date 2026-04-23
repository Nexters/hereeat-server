package com.yogieat.datasource.db.core.restaurant.reservation;

import com.yogieat.restaurant.reservation.domain.RestaurantReservation;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import com.yogieat.restaurant.reservation.service.RestaurantReservationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RestaurantReservationCoreRepository implements RestaurantReservationRepository {

    private final RestaurantReservationJpaRepository jpaRepository;

    @Override
    @Transactional
    public RestaurantReservation save(RestaurantReservation reservation) {
        Optional<RestaurantReservationEntity> existingEntity = jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(
                reservation.restaurantId(),
                reservation.provider()
        );

        RestaurantReservationEntity entity = existingEntity
                .map(found -> {
                    found.apply(reservation);
                    return found;
                })
                .orElseGet(() -> RestaurantReservationEntity.from(reservation));

        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<RestaurantReservation> findActiveByRestaurantId(Long restaurantId) {
        return jpaRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByIdAsc(restaurantId).stream()
                .map(RestaurantReservationEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<RestaurantReservation> findActiveByRestaurantIdAndProvider(Long restaurantId, ReservationProvider provider) {
        return jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(restaurantId, provider)
                .map(RestaurantReservationEntity::toDomain);
    }

    @Override
    @Transactional
    public void softDeleteByRestaurantIdAndProvider(Long restaurantId, ReservationProvider provider) {
        jpaRepository.findByRestaurantIdAndProviderAndDeletedAtIsNull(restaurantId, provider)
                .ifPresent(entity -> {
                    entity.softDeleteEntity();
                    jpaRepository.save(entity);
                });
    }
}
