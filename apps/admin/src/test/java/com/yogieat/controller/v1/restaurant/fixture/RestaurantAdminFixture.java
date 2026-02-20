package com.yogieat.controller.v1.restaurant.fixture;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.controller.v1.restaurant.request.RestaurantRequest;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import java.util.List;

public final class RestaurantAdminFixture {

    private RestaurantAdminFixture() {
    }

    public static RestaurantAdminResult.Detail sampleRestaurantAdminDetail() {
        return RestaurantAdminResult.Detail.of(
                1L,
                "external-id",
                10L,
                LargeCategory.KOREAN,
                "국밥",
                "restaurant",
                "address",
                4.5,
                "image",
                "map",
                "review",
                "description",
                Region.GANGNAM,
                null,
                10,
                20,
                "menu",
                12000,
                "2-3",
                "summary-title",
                List.of("summary-1"),
                null,
                null,
                null
        );
    }

    public static RestaurantAdminResult.Detail sampleUpdatedRestaurantAdminDetail() {
        return RestaurantAdminResult.Detail.of(
                1L,
                "external-id",
                10L,
                LargeCategory.KOREAN,
                "국밥",
                "updated",
                "address",
                4.8,
                "image",
                "map",
                "review",
                "description",
                Region.GANGNAM,
                null,
                10,
                20,
                "menu",
                12000,
                "2-3",
                "summary-title",
                List.of("summary-1"),
                null,
                null,
                null
        );
    }

    public static RestaurantRequest.Patch patchForUpdateName() {
        return RestaurantRequest.Patch.from(
                null,
                null,
                "updated",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RestaurantRequest.Patch patchForInvalidLocation() {
        return RestaurantRequest.Patch.from(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "GANGNAM",
                RestaurantRequest.Patch.RestaurantLocationRequest.of(List.of(127.0)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RestaurantRequest.Patch patchForInvalidRegion() {
        return RestaurantRequest.Patch.from(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "INVALID_REGION",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RestaurantRequest.Patch patchForInvalidTimeSlot() {
        return RestaurantRequest.Patch.from(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "INVALID_SLOT"
        );
    }
}
