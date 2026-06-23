package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.GeoJson;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.region.domain.RegionSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestaurantCollectionPlanTest {

    @Test
    void create_excludes_regions_when_restaurant_count_reaches_limit() {
        List<RestaurantCollectionPlan.Request> requests = RestaurantCollectionPlan.create(List.of(
                summary("FULL", "만석", RestaurantCollectionPlan.REGION_RESTAURANT_LIMIT),
                summary("ALMOST", "거의만석", RestaurantCollectionPlan.REGION_RESTAURANT_LIMIT - 1),
                summary("EMPTY", "부족", 10)
        ), 5);

        assertThat(requests)
                .extracting(request -> request.regionSummary().region().code())
                .containsExactly("ALMOST", "EMPTY");
    }

    @Test
    void create_allocates_more_requests_to_regions_with_more_remaining_slots() {
        List<RestaurantCollectionPlan.Request> requests = RestaurantCollectionPlan.create(List.of(
                summary("ALMOST", "거의만석", RestaurantCollectionPlan.REGION_RESTAURANT_LIMIT - 1),
                summary("EMPTY", "부족", 10)
        ), 5);

        assertThat(requests)
                .extracting(RestaurantCollectionPlan.Request::countPerCategory)
                .containsExactly(1, 18);
    }

    private RegionSummary summary(String code, String displayName, long restaurantCount) {
        return new RegionSummary(
                new RegionMaster(
                        (long) code.hashCode(),
                        code,
                        "서울",
                        displayName,
                        new GeoJson.Point(List.of(127.0, 37.0)),
                        RegionStatus.ACTIVE,
                        0,
                        null,
                        null
                ),
                restaurantCount
        );
    }
}
