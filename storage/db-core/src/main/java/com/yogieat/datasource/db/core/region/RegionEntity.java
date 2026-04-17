package com.yogieat.datasource.db.core.region;

import com.yogieat.common.GeoJson;
import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.region.domain.RegionMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "t_region",
        indexes = {
            @Index(name = "idx_region_active_sort_order", columnList = "is_active, sort_order")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double latitude;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private RegionEntity(
            String code,
            String displayName,
            Double longitude,
            Double latitude,
            boolean active,
            int sortOrder
    ) {
        this.code = code;
        this.displayName = displayName;
        this.longitude = longitude;
        this.latitude = latitude;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public static RegionEntity of(RegionMaster regionMaster) {
        return RegionEntity.builder()
                .code(regionMaster.code())
                .displayName(regionMaster.displayName())
                .longitude(toLongitude(regionMaster.coordinatesStandard()))
                .latitude(toLatitude(regionMaster.coordinatesStandard()))
                .active(regionMaster.active())
                .sortOrder(regionMaster.sortOrder())
                .build();
    }

    public static RegionMaster toDomain(RegionEntity entity) {
        return new RegionMaster(
                entity.getId(),
                entity.getCode(),
                entity.getDisplayName(),
                new GeoJson.Point(List.of(entity.getLongitude(), entity.getLatitude())),
                entity.isActive(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void apply(RegionMaster regionMaster) {
        this.code = regionMaster.code();
        this.displayName = regionMaster.displayName();
        this.longitude = toLongitude(regionMaster.coordinatesStandard());
        this.latitude = toLatitude(regionMaster.coordinatesStandard());
        this.active = regionMaster.active();
        this.sortOrder = regionMaster.sortOrder();
    }

    private static Double toLongitude(GeoJson.Point coordinatesStandard) {
        return coordinatesStandard.getCoordinates().getFirst();
    }

    private static Double toLatitude(GeoJson.Point coordinatesStandard) {
        return coordinatesStandard.getCoordinates().get(1);
    }
}
