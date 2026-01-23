package com.yogieat.domain.gathering.repository;

import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.gathering.entity.GatheringEntity;
import com.yogieat.domain.gathering.service.GatheringRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GatheringCoreRepository implements GatheringRepository {
    private final GatheringJpaRepository gatheringJpaRepository;

    @Override
    public Optional<Gathering> findById(Long id) {
        return gatheringJpaRepository.findById(id)
                .map(GatheringEntity::toDomain);
    }
}
