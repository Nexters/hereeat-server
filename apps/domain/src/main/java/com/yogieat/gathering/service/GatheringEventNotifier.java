package com.yogieat.gathering.service;

import com.yogieat.gathering.domain.result.GatheringResult;

public interface GatheringEventNotifier {
    void notifyParticipantJoined(String accessKey, GatheringResult.ParticipantCount status);
    void notifyGatheringFull(String accessKey, GatheringResult.ParticipantCount status);
}
