package com.yogieat.datasource.db.core.gathering;

import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.service.GatheringRepository;
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

    @Override
    public Optional<Gathering> findByAccessKey(String accessKey) {
        return gatheringJpaRepository.findByAccessKey(accessKey)
                .map(GatheringEntity::toDomain);
    }

    @Override
    public Gathering save(Gathering gathering) {
        GatheringEntity entity = GatheringEntity.from(gathering);
        GatheringEntity savedEntity = gatheringJpaRepository.save(entity);
        return GatheringEntity.toDomain(savedEntity);
    }
}
