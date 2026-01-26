package com.yogieat.domain.gathering.controller;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.CreateGatheringResponse;
import com.yogieat.domain.gathering.service.GatheringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🙋 Gathering API", description = "모임 관련 API")
@RestController
@RequestMapping("/api/v1/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringService gatheringService;

    // 모임 생성 API
    @Operation(summary = "모임 생성", description = "사용자가 모임을 생성합니다.")
    @PostMapping
    public CreateGatheringResponse createGathering(
            @RequestBody CreateGatheringRequest request
    ) {
        return gatheringService.createGathering(request);
    }
}
