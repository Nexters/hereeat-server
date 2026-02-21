package com.yogieat.gathering.result;

import com.yogieat.common.Region;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;
import com.yogieat.restaurant.result.PaginationResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GatheringAdminResult {

    private GatheringAdminResult() {
    }

    public record ListItem(
            Long id,
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            Integer peopleCount,
            Long participantCount,
            Double fillRate,
            LocalDateTime deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ListItem from(Gathering gathering, long participantCount) {
            double fillRate = gatherFillRate(gathering, participantCount);
            return new ListItem(
                    gathering.id(),
                    gathering.accessKey(),
                    gathering.title(),
                    gathering.scheduledDate(),
                    gathering.timeSlot(),
                    gathering.region(),
                    gathering.peopleCount(),
                    participantCount,
                    fillRate,
                    gathering.deletedAt(),
                    gathering.createdAt(),
                    gathering.updatedAt()
            );
        }
    }

    public record Page(
            List<ListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static Page of(List<ListItem> content, int page, int size, long totalElements) {
            PaginationResult pagination = PaginationResult.of(page, size, totalElements);
            return new Page(
                    content,
                    page,
                    size,
                    totalElements,
                    pagination.totalPages(),
                    pagination.hasNext()
            );
        }
    }

    public record GatheringItem(
            Long id,
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            Integer peopleCount,
            LocalDateTime deletedAt,
            Long participantCount,
            Double fillRate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static GatheringItem from(Gathering gathering, long participantCount) {
            double fillRate = gatherFillRate(gathering, participantCount);
            return new GatheringItem(
                    gathering.id(),
                    gathering.accessKey(),
                    gathering.title(),
                    gathering.scheduledDate(),
                    gathering.timeSlot(),
                    gathering.region(),
                    gathering.peopleCount(),
                    gathering.deletedAt(),
                    participantCount,
                    fillRate,
                    gathering.createdAt(),
                    gathering.updatedAt()
            );
        }
    }

    public record Detail(
            GatheringItem gathering,
            List<ParticipantItem> participants,
            long participantCount,
            double fillRate
    ) {
        public static Detail of(Gathering gathering, List<ParticipantItem> participants, long participantCount) {
            return new Detail(
                    GatheringItem.from(gathering, participantCount),
                    participants,
                    participantCount,
                    gatherFillRate(gathering, participantCount)
            );
        }
    }

    public record ParticipantItem(
            Long id,
            String nickname,
            String role,
            String distanceRange,
            List<String> preferences,
            String dislikes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long gatheringId
    ) {
        public static ParticipantItem from(Participant participant) {
            return new ParticipantItem(
                    participant.id(),
                    participant.nickname(),
                    roleToValue(participant.role()),
                    distanceRangeToValue(participant.distanceRange()),
                    splitComma(participant.preferences()),
                    participant.dislikes(),
                    null,
                    null,
                    participant.gatheringId()
            );
        }

        public static ParticipantItem of(
                Long id,
                String nickname,
                String role,
                String distanceRange,
                List<String> preferences,
                String dislikes,
                LocalDateTime createdAt,
                LocalDateTime updatedAt,
                Long gatheringId
        ) {
            return new ParticipantItem(
                    id,
                    nickname,
                    role,
                    distanceRange,
                    preferences,
                    dislikes,
                    createdAt,
                    updatedAt,
                    gatheringId
            );
        }
    }

    public record DataIssue(
            String id,
            String severity,
            String title,
            String description,
            Long relatedId
    ) {
    }

    public record Dashboard(
            LocalDateTime generatedAt,
            List<GatheringItem> gatherings,
            List<ParticipantItem> participants,
            List<DataIssue> issues
    ) {
    }

    private static double gatherFillRate(Gathering gathering, long participantCount) {
        if (gathering.peopleCount() == null || gathering.peopleCount() == 0) {
            return 0.0;
        }

        return ((double) participantCount) / gathering.peopleCount();
    }

    private static String roleToValue(Role role) {
        return role == null ? null : role.name();
    }

    private static String distanceRangeToValue(DistanceRange distanceRange) {
        if (distanceRange == null) {
            return null;
        }
        return distanceRange.name();
    }

    private static List<String> splitComma(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return List.of(value.split(","));
    }
}
