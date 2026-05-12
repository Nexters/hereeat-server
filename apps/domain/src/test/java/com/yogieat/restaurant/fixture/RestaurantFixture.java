package com.yogieat.restaurant.fixture;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantCommand;
import java.util.List;

public final class RestaurantFixture {

    private RestaurantFixture() {
    }

    public static Restaurant sampleSourceRestaurantForAdmin() {
        return new Restaurant(
                1L,
                "external",
                1L,
                "name",
                "address",
                3.2,
                "image",
                "map",
                "review",
                "description",
                Region.fromString("HONGDAE"),
                null,
                1,
                2,
                "menu",
                10000,
                "1",
                "summary-title",
                List.of("before"),
                TimeSlot.LUNCH,
                null,
                null,
                null,
                null,
                true
        );
    }

    public static Restaurant sampleUpdatedRestaurantForAdmin(Restaurant source) {
        return new Restaurant(
                source.id(),
                "updated-external",
                10L,
                "updated-name",
                "updated-address",
                4.7,
                "updated-image",
                "updated-map",
                "updated-review",
                "updated-description",
                Region.fromString("GANGNAM"),
                source.location(),
                11,
                21,
                "updated-menu",
                12000,
                "2-3",
                "updated-summary-title",
                List.of("summary"),
                TimeSlot.BOTH,
                source.createdAt(),
                source.updatedAt(),
                null,
                null,
                true
        );
    }

    public static RestaurantCommand.Patch samplePatchForAdmin() {
        return new RestaurantCommand.Patch(
                "updated-external",
                "updated-name",
                "updated-address",
                10L,
                Region.fromString("GANGNAM"),
                4.7,
                "updated-image",
                "updated-map",
                "updated-review",
                "updated-description",
                null,
                11,
                21,
                "updated-menu",
                12000,
                "2-3",
                "updated-summary-title",
                List.of("summary"),
                TimeSlot.BOTH,
                true
        );
    }

}
