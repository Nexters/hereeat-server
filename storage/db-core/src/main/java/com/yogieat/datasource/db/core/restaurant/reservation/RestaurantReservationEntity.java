package com.yogieat.datasource.db.core.restaurant.reservation;

import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.restaurant.reservation.domain.RestaurantReservation;
import com.yogieat.restaurant.reservation.domain.value.ReservationProvider;
import com.yogieat.restaurant.reservation.domain.value.ReservationSource;
import com.yogieat.restaurant.reservation.domain.value.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "t_restaurant_reservation",
        indexes = {
                @Index(
                        name = "idx_restaurant_reservation_restaurant_id_deleted_at",
                        columnList = "restaurant_id, deleted_at"
                ),
                @Index(
                        name = "idx_restaurant_reservation_provider_status_deleted_at",
                        columnList = "provider, status, deleted_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReservationEntity extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private ReservationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ReservationSource source;

    @Column(name = "reservation_url", length = 500)
    private String reservationUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "provider_place_key")
    private String providerPlaceKey;

    @Column(name = "matched_name")
    private String matchedName;

    @Column(name = "matched_address")
    private String matchedAddress;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RestaurantReservationEntity(
            Long restaurantId,
            ReservationProvider provider,
            ReservationStatus status,
            ReservationSource source,
            String reservationUrl,
            String note,
            String providerPlaceKey,
            String matchedName,
            String matchedAddress,
            Double matchScore,
            LocalDateTime matchedAt,
            LocalDateTime verifiedAt,
            LocalDateTime rejectedAt,
            LocalDateTime lastCheckedAt
    ) {
        this.restaurantId = restaurantId;
        this.provider = provider;
        this.status = status;
        this.source = source;
        this.reservationUrl = reservationUrl;
        this.note = note;
        this.providerPlaceKey = providerPlaceKey;
        this.matchedName = matchedName;
        this.matchedAddress = matchedAddress;
        this.matchScore = matchScore;
        this.matchedAt = matchedAt;
        this.verifiedAt = verifiedAt;
        this.rejectedAt = rejectedAt;
        this.lastCheckedAt = lastCheckedAt;
    }

    public static RestaurantReservationEntity from(RestaurantReservation reservation) {
        return RestaurantReservationEntity.builder()
                .restaurantId(reservation.restaurantId())
                .provider(reservation.provider())
                .status(reservation.status())
                .source(reservation.source())
                .reservationUrl(reservation.reservationUrl())
                .note(reservation.note())
                .providerPlaceKey(reservation.providerPlaceKey())
                .matchedName(reservation.matchedName())
                .matchedAddress(reservation.matchedAddress())
                .matchScore(reservation.matchScore())
                .matchedAt(reservation.matchedAt())
                .verifiedAt(reservation.verifiedAt())
                .rejectedAt(reservation.rejectedAt())
                .lastCheckedAt(reservation.lastCheckedAt())
                .build();
    }

    public RestaurantReservation toDomain() {
        return new RestaurantReservation(
                getId(),
                restaurantId,
                provider,
                status,
                source,
                reservationUrl,
                note,
                providerPlaceKey,
                matchedName,
                matchedAddress,
                matchScore,
                matchedAt,
                verifiedAt,
                rejectedAt,
                lastCheckedAt,
                getCreatedAt(),
                getUpdatedAt()
        );
    }

    public void apply(RestaurantReservation reservation) {
        this.status = reservation.status();
        this.source = reservation.source();
        this.reservationUrl = reservation.reservationUrl();
        this.note = reservation.note();
        this.providerPlaceKey = reservation.providerPlaceKey();
        this.matchedName = reservation.matchedName();
        this.matchedAddress = reservation.matchedAddress();
        this.matchScore = reservation.matchScore();
        this.matchedAt = reservation.matchedAt();
        this.verifiedAt = reservation.verifiedAt();
        this.rejectedAt = reservation.rejectedAt();
        this.lastCheckedAt = reservation.lastCheckedAt();
    }

    public void softDeleteEntity() {
        super.softDelete();
    }
}
