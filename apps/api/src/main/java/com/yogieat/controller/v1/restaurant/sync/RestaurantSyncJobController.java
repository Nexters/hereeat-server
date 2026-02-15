package com.yogieat.controller.v1.restaurant.sync;

import com.yogieat.controller.v1.restaurant.sync.response.CreateRestaurantSyncJobResponse;
import com.yogieat.controller.v1.restaurant.sync.response.GetRestaurantSyncJobResponse;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import com.yogieat.restaurant.sync.service.RestaurantSyncJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🍜 Restaurant Sync Job API", description = "맛집 동기화 작업 API")
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantSyncJobController {

    private final RestaurantSyncJobService restaurantSyncJobService;

    @Operation(summary = "전체 동기화 Job 생성", description = "모든 맛집 데이터 동기화 Job을 생성합니다.")
    @PostMapping("/sync-jobs/all")
    public CreateRestaurantSyncJobResponse createAllSyncJob() {
        return CreateRestaurantSyncJobResponse.from(
                restaurantSyncJobService.createAllJob(RestaurantSyncTriggerType.MANUAL)
        );
    }

    @Operation(summary = "단건 동기화 Job 생성", description = "특정 맛집 ID에 대한 동기화 Job을 생성합니다.")
    @PostMapping("/{restaurantId}/sync-jobs")
    public CreateRestaurantSyncJobResponse createSingleSyncJob(@PathVariable Long restaurantId) {
        return CreateRestaurantSyncJobResponse.from(
                restaurantSyncJobService.createSingleJob(restaurantId, RestaurantSyncTriggerType.MANUAL)
        );
    }

    @Operation(summary = "동기화 Job 조회", description = "동기화 Job 상태를 조회합니다.")
    @GetMapping("/sync-jobs/{jobId}")
    public GetRestaurantSyncJobResponse getSyncJob(@PathVariable Long jobId) {
        return GetRestaurantSyncJobResponse.from(restaurantSyncJobService.getJob(jobId));
    }
}
