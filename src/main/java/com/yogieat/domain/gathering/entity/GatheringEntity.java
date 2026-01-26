package com.yogieat.domain.gathering.entity;

import com.yogieat.domain.common.Region;
import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_gathering")
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

    @Column(name = "people_count")
    private Integer peopleCount;

    @Builder(access = AccessLevel.PRIVATE)
    public GatheringEntity(
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            int peopleCount) {
        this.accessKey = accessKey;
        this.title = title;
        this.scheduledDate = scheduledDate;
        this.timeSlot = timeSlot;
        this.region = region;
        this.peopleCount = peopleCount;
    }

    public static Gathering toDomain(GatheringEntity entity) {
        return new Gathering(
                entity.getId(),
                entity.getAccessKey(),
                entity.getTitle(),
                entity.getScheduledDate(),
                entity.getTimeSlot(),
                entity.getRegion(),
                entity.getPeopleCount(),
                entity.getDeletedAt()
        );
    }
}
