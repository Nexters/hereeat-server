package com.yogieat.controller.v1.gathering.response;

import com.yogieat.gathering.result.GatheringAdminResult;
import com.yogieat.gathering.result.GatheringAdminResult.GatheringItem;
import com.yogieat.gathering.result.GatheringAdminResult.ListItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GatheringAdminResponse {

    private GatheringAdminResponse() {
    }

    public record ListResponse(
            List<ListItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static ListResponse from(GatheringAdminResult.Page result) {
            List<ListItemResponse> content = result.content().stream()
                    .map(ListItemResponse::from)
                    .toList();

            return new ListResponse(
                    content,
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages(),
                    result.hasNext()
            );
        }
    }

    public record ListItemResponse(
            Long id,
            String accessKey,
            String title,
            LocalDate scheduledDate,
            String timeSlot,
            String region,
            Integer peopleCount,
            LocalDateTime deletedAt,
            Long participantCount,
            Double fillRate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ListItemResponse from(ListItem result) {
            return new ListItemResponse(
                    result.id(),
                    result.accessKey(),
                    result.title(),
                    result.scheduledDate(),
                    result.timeSlot() != null ? result.timeSlot().name() : null,
                    result.region() != null ? result.region().name() : null,
                    result.peopleCount(),
                    result.deletedAt(),
                    result.participantCount(),
                    result.fillRate(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }
    }

    public record GatheringResponse(
            Long id,
            String accessKey,
            String title,
            String region,
            Integer peopleCount,
            LocalDate scheduledDate,
            String timeSlot,
            LocalDateTime deletedAt,
            Long participantCount,
            Double fillRate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static GatheringResponse from(GatheringItem result) {
            return new GatheringResponse(
                    result.id(),
                    result.accessKey(),
                    result.title(),
                    result.region() != null ? result.region().name() : null,
                    result.peopleCount(),
                    result.scheduledDate(),
                    result.timeSlot() != null ? result.timeSlot().name() : null,
                    result.deletedAt(),
                    result.participantCount(),
                    result.fillRate(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }
    }

    public record DetailResponse(
            GatheringResponse gathering,
            List<ParticipantItemResponse> participants,
            long participantCount,
            double fillRate
    ) {
        public static DetailResponse from(GatheringAdminResult.Detail result) {
            List<ParticipantItemResponse> participants = result.participants().stream()
                    .map(ParticipantItemResponse::from)
                    .toList();

            return new DetailResponse(
                    GatheringResponse.from(result.gathering()),
                    participants,
                    result.participantCount(),
                    result.fillRate()
            );
        }
    }

    public record ParticipantItemResponse(
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
        public static ParticipantItemResponse from(GatheringAdminResult.ParticipantItem result) {
            return new ParticipantItemResponse(
                    result.id(),
                    result.nickname(),
                    result.role(),
                    result.distanceRange(),
                    result.preferences(),
                    result.dislikes(),
                    result.createdAt(),
                    result.updatedAt(),
                    result.gatheringId()
            );
        }
    }

    public record DataIssueResponse(
            String id,
            String severity,
            String title,
            String description,
            Long relatedId
    ) {
        public static DataIssueResponse from(GatheringAdminResult.DataIssue result) {
            return new DataIssueResponse(
                    result.id(),
                    result.severity(),
                    result.title(),
                    result.description(),
                    result.relatedId()
            );
        }
    }

    public record DashboardResponse(
            LocalDateTime generatedAt,
            List<GatheringResponse> gatherings,
            List<ParticipantItemResponse> participants,
            List<DataIssueResponse> issues
    ) {
        public static DashboardResponse from(GatheringAdminResult.Dashboard result) {
            List<GatheringResponse> gatherings = result.gatherings().stream()
                    .map(GatheringResponse::from)
                    .toList();

            List<ParticipantItemResponse> participants = result.participants().stream()
                    .map(ParticipantItemResponse::from)
                    .toList();

            List<DataIssueResponse> issues = result.issues().stream()
                    .map(DataIssueResponse::from)
                    .toList();

            return new DashboardResponse(
                    result.generatedAt(),
                    gatherings,
                    participants,
                    issues
            );
        }
    }
}
