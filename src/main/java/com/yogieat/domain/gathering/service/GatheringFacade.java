package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.CreateGatheringResponse;
import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.participant.service.ParticipantService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatheringFacade {

    private final GatheringService gatheringService;
    private final ParticipantService participantService;

    @Transactional
    public CreateGatheringResponse createGathering(CreateGatheringRequest request) {
        Gathering gathering = gatheringService.create(request);
        Long gatheringId = gathering.id();
        participantService.createHostParticipant(request, gatheringId);

        return new CreateGatheringResponse(gathering.accessKey());
    }
}
