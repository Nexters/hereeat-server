package com.yogieat.participant.service;

import com.yogieat.participant.domain.Participant;
import java.util.List;

public interface ParticipantRepository {
    Participant save(Participant participant);
    long countByGatheringId(Long gatheringId);
    List<Participant> findByGatheringId(Long gatheringId);
}
