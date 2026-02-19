package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.Gathering;
import java.util.List;
import java.util.Optional;

public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
    Gathering save(Gathering gathering);

    List<Gathering> findAdminGatherings(
            GatheringAdminListCriteria criteria,
            int page,
            int size
    );

    List<Gathering> findAdminGatherings(GatheringAdminListCriteria criteria);

    long countAdminGatherings(GatheringAdminListCriteria criteria);
}
