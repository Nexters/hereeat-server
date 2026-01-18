package com.yogieat.domain.gathering.repository;

import com.yogieat.domain.gathering.service.GatheringRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GatheringCoreRepository implements GatheringRepository {
    private final GatheringJpaRepository gatheringJpaRepository;
}
