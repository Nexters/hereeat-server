package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.result.GatheringAdminItemResult;
import java.util.List;
import java.util.Optional;

public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
    Optional<Gathering> findByAccessKeyForUpdate(String accessKey);
    Gathering save(Gathering gathering);

    List<Gathering> findAdminGatherings(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    );

    List<GatheringAdminItemResult> findAdminGatheringsWithParticipantCount(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    );

    List<Gathering> findAdminGatherings(GatheringAdminCriteria.List criteria);

    long countAdminGatherings(GatheringAdminCriteria.List criteria);
}
