package com.yogieat.domain.participant.repository;

import com.yogieat.domain.participant.service.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParticipantCoreRepository implements ParticipantRepository {
    private final ParticipantJpaRepository participantJpaRepository;
}
