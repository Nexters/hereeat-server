package com.yogieat.gathering.facade;

import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.result.GatheringAdminResult;
import com.yogieat.gathering.service.GatheringAdminListCriteria;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.service.ParticipantService;
import com.yogieat.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GatheringAdminFacade {

    private final GatheringService gatheringService;
    private final ParticipantService participantService;

    public GatheringAdminResult.Page getPageGatherings(
            int page,
            int size,
            String keyword,
            String region,
            String timeSlot,
            Boolean includeDeleted
    ) {
        GatheringAdminListCriteria criteria = GatheringAdminListCriteria.of(
                normalizeKeyword(keyword),
                Region.fromString(region),
                parseTimeSlot(timeSlot),
                includeDeleted != null && includeDeleted
        );

        List<Gathering> gatherings = gatheringService.findAdminGatherings(criteria, page, size);
        long totalElements = gatheringService.countAdminGatherings(criteria);

        List<GatheringAdminResult.ListItem> content = gatherings.stream()
                .map(gathering -> GatheringAdminResult.ListItem.from(
                        gathering,
                        participantService.countByGatheringId(gathering.id())
                ))
                .toList();

        return GatheringAdminResult.Page.of(content, page, size, totalElements);
    }

    public GatheringAdminResult.Detail getGatheringBy(Long gatheringId) {
        Gathering gathering = gatheringService.getGatheringBy(gatheringId);
        List<Participant> participants = participantService.getByGatheringId(gathering.id());

        List<GatheringAdminResult.ParticipantItem> participantItems = participants.stream()
                .map(this::toParticipantItem)
                .toList();

        long participantCount = participants.size();
        return GatheringAdminResult.Detail.of(gathering, participantItems, participantCount);
    }

    public GatheringAdminResult.Dashboard getGatheringDashboard() {
        LocalDateTime generatedAt = LocalDateTime.now();
        GatheringAdminListCriteria criteria = GatheringAdminListCriteria.of(
                null,
                null,
                null,
                false
        );

        List<Gathering> gatherings = gatheringService.findAdminGatherings(criteria);

        List<GatheringAdminResult.GatheringItem> gatheringItems = new ArrayList<>();
        List<GatheringAdminResult.ParticipantItem> participantItems = new ArrayList<>();

        for (Gathering gathering : gatherings) {
            List<Participant> participants = participantService.getByGatheringId(gathering.id());
            long participantCount = participants.size();

            gatheringItems.add(GatheringAdminResult.GatheringItem.from(gathering, participantCount));
            participants.forEach(participant -> participantItems.add(toParticipantItem(participant)));
        }

        return new GatheringAdminResult.Dashboard(
                generatedAt,
                gatheringItems,
                participantItems,
                List.of()
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private TimeSlot parseTimeSlot(String timeSlot) {
        if (timeSlot == null || timeSlot.isBlank()) {
            return null;
        }

        try {
            return TimeSlot.valueOf(timeSlot.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }
    }

    private GatheringAdminResult.ParticipantItem toParticipantItem(Participant participant) {
        return new GatheringAdminResult.ParticipantItem(
                participant.id(),
                participant.nickname(),
                participant.role() == null ? null : participant.role().name(),
                participant.distanceRange() == null ? null : participant.distanceRange().name(),
                StringUtils.splitByComma(participant.preferences()),
                participant.dislikes(),
                null,
                null,
                participant.gatheringId()
        );
    }
}
