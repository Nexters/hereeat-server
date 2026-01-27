package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.CreateGatheringResponse;
import com.yogieat.domain.gathering.domain.Gathering;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatheringFacade {

    private final GatheringService gatheringService;

    @Transactional
    public CreateGatheringResponse createGathering(CreateGatheringRequest request) {
        Gathering gathering = gatheringService.create(request);
        return new CreateGatheringResponse(gathering.accessKey());
    }
}
