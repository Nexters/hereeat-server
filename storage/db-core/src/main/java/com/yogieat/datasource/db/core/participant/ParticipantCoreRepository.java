package com.yogieat.datasource.db.core.participant;

import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.service.ParticipantRepository;
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

    @Override
    public boolean existsByGatheringIdAndNickname(Long gatheringId, String nickname) {
        return participantJpaRepository.existsByGatheringIdAndNickname(gatheringId, nickname);
    }
}
