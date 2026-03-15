package com.yogieat.recommend.service;

import org.springframework.stereotype.Component;

@Component
public class RecommendRerollPolicy {

    private static final long MAX_REROLL_COUNT = 1L;

    public boolean isRerollLimitExceeded(long rerollCount) {
        return rerollCount >= MAX_REROLL_COUNT;
    }

    public long maxRerollCount() {
        return MAX_REROLL_COUNT;
    }
}
