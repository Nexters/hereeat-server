package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.result.GatheringResult;

public interface GatheringEventNotifier {
    void notifyParticipantJoined(String accessKey, GatheringResult.ParticipantCount status);
    void notifyRecommendResultCreated(String accessKey, GatheringResult.ParticipantCount status);
}
