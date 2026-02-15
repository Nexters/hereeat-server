package com.yogieat.datasource.db.core.participant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantEntity, Long> {
    long countByGatheringId(Long gatheringId);
    List<ParticipantEntity> findByGatheringId(Long gatheringId);
    boolean existsByGatheringIdAndNickname(Long gatheringId, String nickname);
}
