package com.yogieat.domain.participant.service;

import com.yogieat.domain.participant.domain.Participant;
import java.util.List;

public interface ParticipantRepository {
    Participant save(Participant participant);
    long countByGatheringId(Long gatheringId);
    List<Participant> findByGatheringId(Long gatheringId);
}
