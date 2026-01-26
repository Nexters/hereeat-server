package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.domain.Gathering;
import java.util.Optional;

public interface GatheringRepository {
    Optional<Gathering> findById(Long id);
    Optional<Gathering> findByAccessKey(String accessKey);
}
