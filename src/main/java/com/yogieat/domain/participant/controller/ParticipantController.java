package com.yogieat.domain.participant.controller;

import com.yogieat.domain.participant.controller.request.CreateParticipantRequest;
import com.yogieat.domain.participant.controller.response.CreateParticipantResponse;
import com.yogieat.domain.participant.service.ParticipantFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🙋 Participant API", description = "참여 관련 API")
@RestController
@RequestMapping("/api/v1/participants")
@RequiredArgsConstructor
public class ParticipantController {
    private final ParticipantFacade participantFacade;

    // 참여 API
    @Operation(summary = "모임 참여", description = "사용자가 모임에 참여합니다.")
    @PostMapping
    public CreateParticipantResponse participateInGathering(
            @RequestBody CreateParticipantRequest request
    ) {
        return CreateParticipantResponse.from(
                participantFacade.participate(request.toCommand())
        );
    }
}
