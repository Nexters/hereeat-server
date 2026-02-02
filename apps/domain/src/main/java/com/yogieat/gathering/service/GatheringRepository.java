package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.Gathering;
import java.util.Optional;

public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
    Gathering save(Gathering gathering);
}
