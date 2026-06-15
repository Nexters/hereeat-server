package com.yogieat.controller.v2.recommend;

import com.yogieat.controller.v2.recommend.response.GetRecommendResultResponse;
import com.yogieat.recommend.service.RecommendResultFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🍽 RecommendResult API (v2)", description = "추천 결과 관련 API (v2)")
@RestController
@RequestMapping("/api/v2/recommend-results")
@RequiredArgsConstructor
public class RecommendResultV2Controller {

    private final RecommendResultFacade recommendResultFacade;

    @Operation(summary = "추천 결과 조회", description = "모임의 추천 결과를 조회합니다.")
    @GetMapping("/{accessKey}")
    public GetRecommendResultResponse getRecommendResults(
            @PathVariable String accessKey
    ) {
        return GetRecommendResultResponse.from(
                recommendResultFacade.getRecommendResultsV2(accessKey)
        );
    }
}
