package com.yogieat.controller.v1.gathering.fixture;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.result.GatheringAdminResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GatheringAdminFixture {

    private GatheringAdminFixture() {
    }

    public static GatheringAdminResult.Page samplePage() {
        return GatheringAdminResult.Page.of(
                List.of(sampleListItem()),
                0,
                10,
                1L
        );
    }

    public static GatheringAdminResult.ListItem sampleListItem() {
        return new GatheringAdminResult.ListItem(
                1L,
                "AK-001",
                "점심 모임",
                LocalDate.of(2026, 2, 20),
                TimeSlot.LUNCH,
                Region.GANGNAM,
                10,
                4L,
                0.4,
                null,
                LocalDateTime.of(2026, 2, 1, 10, 0),
                LocalDateTime.of(2026, 2, 1, 10, 0)
        );
    }

    public static GatheringAdminResult.GatheringItem sampleGatheringItem() {
        return new GatheringAdminResult.GatheringItem(
                1L,
                "AK-001",
                "점심 모임",
                LocalDate.of(2026, 2, 20),
                TimeSlot.LUNCH,
                Region.GANGNAM,
                10,
                null,
                4L,
                0.4,
                LocalDateTime.of(2026, 2, 1, 10, 0),
                LocalDateTime.of(2026, 2, 1, 10, 0)
        );
    }

    public static GatheringAdminResult.Detail sampleDetail() {
        GatheringAdminResult.GatheringItem gathering = sampleGatheringItem();
        return new GatheringAdminResult.Detail(
                gathering,
                List.of(sampleParticipantItem(gathering.id())),
                4L,
                0.4
        );
    }

    public static GatheringAdminResult.ParticipantItem sampleParticipantItem(Long gatheringId) {
        return new GatheringAdminResult.ParticipantItem(
                1L,
                "admin-user",
                "HOST",
                "NEAR",
                List.of("korean", "japanese"),
                null,
                LocalDateTime.of(2026, 2, 1, 10, 10),
                LocalDateTime.of(2026, 2, 1, 10, 10),
                gatheringId
        );
    }

    public static GatheringAdminResult.Dashboard sampleDashboard() {
        return new GatheringAdminResult.Dashboard(
                LocalDateTime.of(2026, 2, 1, 11, 0),
                List.of(sampleGatheringItem()),
                List.of(sampleParticipantItem(1L)),
                List.of(
                        new GatheringAdminResult.DataIssue(
                                "I-001",
                                "INFO",
                                "sample issue",
                                "sample description",
                                1L
                        )
                )
        );
    }
}
