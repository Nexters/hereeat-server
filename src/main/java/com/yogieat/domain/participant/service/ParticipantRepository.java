package com.yogieat.domain.participant.service;

import com.yogieat.domain.participant.domain.Participant;

public interface ParticipantRepository {
    Participant save(Participant participant);
    long countByGatheringId(Long gatheringId);
}
