package com.yogieat.domain.participant.repository;

import com.yogieat.domain.participant.domain.Participant;
import com.yogieat.domain.participant.entity.ParticipantEntity;
import com.yogieat.domain.participant.service.ParticipantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParticipantCoreRepository implements ParticipantRepository {
    private final ParticipantJpaRepository participantJpaRepository;

    @Override
    public Participant save(Participant participant) {
        ParticipantEntity entity = ParticipantEntity.from(participant);
        ParticipantEntity savedEntity = participantJpaRepository.save(entity);
        return ParticipantEntity.toDomain(savedEntity);
    }

    @Override
    public long countByGatheringId(Long gatheringId) {
        return participantJpaRepository.countByGatheringId(gatheringId);
    }

    @Override
    public List<Participant> findByGatheringId(Long gatheringId) {
        List<ParticipantEntity> entities = participantJpaRepository.findByGatheringId(gatheringId);
        return entities.stream()
                .map(ParticipantEntity::toDomain)
                .toList();
    }
}
