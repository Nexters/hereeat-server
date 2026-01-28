package com.yogieat.domain.gathering.controller;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.CreateGatheringResponse;
import com.yogieat.domain.gathering.controller.response.GetGatheringResponse;
import com.yogieat.domain.gathering.service.GatheringFacade;
import com.yogieat.domain.gathering.service.GatheringService;
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

    // 모임 생성 API
    @Operation(summary = "모임 생성", description = "사용자가 모임을 생성합니다.")
    @PostMapping
    public CreateGatheringResponse createGathering(
            @RequestBody @Valid CreateGatheringRequest request
    ) {
        return gatheringFacade.createGathering(request);
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
}
