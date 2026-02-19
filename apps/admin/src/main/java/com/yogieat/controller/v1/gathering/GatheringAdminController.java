package com.yogieat.controller.v1.gathering;

import com.yogieat.controller.v1.gathering.response.GatheringAdminResponse;
import com.yogieat.gathering.facade.GatheringAdminFacade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/gatherings")
@Validated
@RequiredArgsConstructor
public class GatheringAdminController {

    private final GatheringAdminFacade gatheringAdminFacade;

    @GetMapping
    public GatheringAdminResponse.ListResponse listGatherings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Positive int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String timeSlot,
            @RequestParam(defaultValue = "false") Boolean includeDeleted
    ) {
        return GatheringAdminResponse.ListResponse.from(
                gatheringAdminFacade.getPageGatherings(
                        page,
                        size,
                        keyword,
                        region,
                        timeSlot,
                        includeDeleted
                )
        );
    }

    @GetMapping("/dashboard")
    public GatheringAdminResponse.DashboardResponse getGatheringDashboard() {
        return GatheringAdminResponse.DashboardResponse.from(
                gatheringAdminFacade.getGatheringDashboard()
        );
    }

    @GetMapping("/{gatheringId}")
    public GatheringAdminResponse.DetailResponse getGatheringById(
            @PathVariable Long gatheringId
    ) {
        return GatheringAdminResponse.DetailResponse.from(
                gatheringAdminFacade.getGatheringBy(gatheringId)
        );
    }
}
