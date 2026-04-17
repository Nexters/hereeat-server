package com.yogieat.datasource.db.core.gathering;

import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "t_gathering",
        indexes = {
            @Index(name = "idx_gathering_region_id", columnList = "region_id"),
            @Index(name = "idx_gathering_region_id_deleted_at", columnList = "region_id, deleted_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringEntity extends BaseEntity {

    @Column(name = "access_key")
    private String accessKey;

    @Column(name = "title")
    private String title;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "time_slot", columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private TimeSlot timeSlot;

    @Column(name = "region")
    @Enumerated(EnumType.STRING)
    private Region region;

    @Column(name = "region_id")
    private Long regionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", insertable = false, updatable = false)
    private RegionEntity regionReference;

    @Column(name = "people_count")
    private Integer peopleCount;

    @Builder(access = AccessLevel.PRIVATE)
    public GatheringEntity(
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            Long regionId,
            int peopleCount) {
        this.accessKey = accessKey;
        this.title = title;
        this.scheduledDate = scheduledDate;
        this.timeSlot = timeSlot;
        this.region = region;
        this.regionId = regionId;
        this.peopleCount = peopleCount;
    }

    public static GatheringEntity from(Gathering gathering) {
        return from(gathering, null);
    }

    public static GatheringEntity from(Gathering gathering, Long regionId) {
        return GatheringEntity.builder()
                .accessKey(gathering.accessKey())
                .title(gathering.title())
                .scheduledDate(gathering.scheduledDate())
                .timeSlot(gathering.timeSlot())
                .region(gathering.region())
                .regionId(regionId)
                .peopleCount(gathering.peopleCount())
                .build();
    }

    public static Gathering toDomain(GatheringEntity entity) {
        return new Gathering(
                entity.getId(),
                entity.getAccessKey(),
                entity.getTitle(),
                entity.getScheduledDate(),
                entity.getTimeSlot(),
                entity.resolveRegion(),
                entity.getPeopleCount(),
                entity.getDeletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public Region resolveRegion() {
        if (regionReference != null) {
            Region resolved = Region.fromString(regionReference.getCode());
            if (resolved != null) {
                return resolved;
            }
        }

        return region;
    }
}
