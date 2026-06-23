package com.yogieat.datasource.db.core.region;

import com.yogieat.common.GeoJson;
import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
            @Index(name = "idx_region_status_sort_order", columnList = "status, sort_order")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, length = 50)
    private String province;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double latitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegionStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private RegionEntity(
            String code,
            String province,
            String displayName,
            Double longitude,
            Double latitude,
            RegionStatus status,
            int sortOrder
    ) {
        this.code = code;
        this.province = province;
        this.displayName = displayName;
        this.longitude = longitude;
        this.latitude = latitude;
        this.status = status;
        this.sortOrder = sortOrder;
    }

    public static RegionEntity of(RegionMaster regionMaster) {
        return RegionEntity.builder()
                .code(regionMaster.code())
                .province(regionMaster.province())
                .displayName(regionMaster.displayName())
                .longitude(toLongitude(regionMaster.coordinatesStandard()))
                .latitude(toLatitude(regionMaster.coordinatesStandard()))
                .status(regionMaster.status())
                .sortOrder(regionMaster.sortOrder())
                .build();
    }

    public static RegionMaster toDomain(RegionEntity entity) {
        return new RegionMaster(
                entity.getId(),
                entity.getCode(),
                entity.getProvince(),
                entity.getDisplayName(),
                new GeoJson.Point(List.of(entity.getLongitude(), entity.getLatitude())),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void apply(RegionMaster regionMaster) {
        this.code = regionMaster.code();
        this.province = regionMaster.province();
        this.displayName = regionMaster.displayName();
        this.longitude = toLongitude(regionMaster.coordinatesStandard());
        this.latitude = toLatitude(regionMaster.coordinatesStandard());
        this.status = regionMaster.status();
        this.sortOrder = regionMaster.sortOrder();
    }

    public void softDelete() {
        super.softDelete();
    }

    private static Double toLongitude(GeoJson.Point coordinatesStandard) {
        return coordinatesStandard.getCoordinates().getFirst();
    }

    private static Double toLatitude(GeoJson.Point coordinatesStandard) {
        return coordinatesStandard.getCoordinates().get(1);
    }
}
