package com.yogieat.recommend.service;

import com.yogieat.recommend.domain.RecommendResultFailed;

public interface RecommendResultFailedRepository {
    RecommendResultFailed save(RecommendResultFailed recommendResultFailed);
}
