package com.yogieat.domain.participant.entity;

import com.yogieat.domain.participant.domain.Participant;
import com.yogieat.domain.participant.domain.value.Role;
import com.yogieat.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_participant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantEntity extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "gathering_id")
    private Long gatheringId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Builder(access = AccessLevel.PRIVATE)
    public ParticipantEntity(
            Long userId,
            Long gatheringId,
            Role role) {
        this.userId = userId;
        this.gatheringId = gatheringId;
        this.role = role;
    }

    public static Participant toDomain(ParticipantEntity entity) {
        return new Participant(
                entity.getId(),
                entity.getUserId(),
                entity.getGatheringId(),
                entity.getRole()
        );
    }
}
