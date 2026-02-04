package com.yogieat.controller.v1.gathering;

import com.yogieat.controller.v1.gathering.request.CreateGatheringRequest;
import com.yogieat.controller.v1.gathering.response.CreateGatheringResponse;
import com.yogieat.controller.v1.gathering.response.GetGatheringResponse;
import com.yogieat.controller.v1.gathering.response.GetParticipantCountResponse;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringFacade;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.service.ParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🙋 Gathering API", description = "모임 관련 API")
@RestController
@RequestMapping("/api/v1/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringFacade gatheringFacade;
    private final GatheringService gatheringService;
    private final ParticipantService participantService;

    // 모임 생성 API
    @Operation(summary = "모임 생성", description = "사용자가 모임을 생성합니다.")
    @PostMapping
    public CreateGatheringResponse createGathering(
            @RequestBody @Valid CreateGatheringRequest request
    ) {
        GatheringResult.Create result = gatheringFacade.createGathering(request.toCommand());
        return CreateGatheringResponse.from(result);
    }

    // 모임 단건 조회 API
    @Operation(summary = "모임 단건 조회", description = "accessKey로 모임을 조회합니다.")
    @GetMapping("/{accessKey}")
    public GetGatheringResponse getGatheringByAccessKey(
            @PathVariable String accessKey
    ) {
        return GetGatheringResponse.from(
                gatheringService.getGatheringByAccessKey(accessKey)
        );
    }

    // 모임 참여 현황 조회 API
    @Operation(summary = "모임 참여자 현황 조회", description = "모임의 참여자 현황을 조회합니다.")
    @GetMapping("/{accessKey}/capacity")
    public GetParticipantCountResponse getParticipantStatus(
            @PathVariable String accessKey
    ) {
        GatheringResult.ParticipantCount result = gatheringService.getGatheringParticipantStatus(accessKey);
        return GetParticipantCountResponse.from(result);
    }
}
