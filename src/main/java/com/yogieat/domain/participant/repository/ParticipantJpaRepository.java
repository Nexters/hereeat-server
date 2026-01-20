package com.yogieat.domain.participant.repository;

import com.yogieat.domain.participant.entity.ParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantEntity, Long> {
}
