package com.yogieat.domain.participant.controller;

import com.yogieat.domain.participant.controller.request.CreateParticipantRequest;
import com.yogieat.domain.participant.controller.response.CreateParticipantResponse;
import com.yogieat.domain.participant.service.ParticipantFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class ParticipantController {
    private final ParticipantFacade participantFacade;

    // 참여 API
    @PostMapping
    public CreateParticipantResponse participateInGathering(
            @RequestBody CreateParticipantRequest request
    ) {
        return CreateParticipantResponse.from(
                participantFacade.participate(request.toCommand())
        );
    }
}
