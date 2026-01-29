package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.GetParticipantCountResponse;
import com.yogieat.domain.gathering.domain.Gathering;
import com.yogieat.domain.participant.service.ParticipantService;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatheringService {
    private final GatheringValidator gatheringValidator;
    private final GatheringRepository gatheringRepository;
    private final ParticipantService participantService;

    /**
     * Gathering 참여 인원이 가득 찼는지 검증
     * GatheringValidator에게 위임
     *
     * @param gathering 검증할 모임
     * @param currentParticipantCount 현재 참여자 수
     */
    public void validateGatheringNotFull(Gathering gathering, long currentParticipantCount) {
        gatheringValidator.validateGatheringNotFull(gathering, currentParticipantCount);
    }

    /**
     * Gathering 생성
     *
     * @param request 모임 생성 요청
     * @return 생성된 Gathering
     */
    @Transactional
    public Gathering create(CreateGatheringRequest request) {
        gatheringValidator.validateCreate(request);

        Gathering gathering = new Gathering(
                null,
                createAccessKey(),
                null,
                request.scheduledDate(),
                request.timeSlot(),
                request.region(),
                request.peopleCount(),
                null
        );

        return gatheringRepository.save(gathering);
    }

    @Transactional(readOnly = true)
    public Gathering getGatheringByAccessKey(String accessKey) {
        Gathering gathering = gatheringRepository.findByAccessKey(accessKey)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        gatheringValidator.validateGatheringNotDeleted(gathering);
        return gathering;
    }

    @Transactional(readOnly = true)
    public GetParticipantCountResponse getGatheringParticipantStatus(String accessKey) {
        Gathering gathering = getGatheringByAccessKey(accessKey);

        long currentCount = participantService.countByGatheringId(gathering.id());
        return new GetParticipantCountResponse(
                currentCount,
                gathering.peopleCount()
        );
    }

    private String createAccessKey() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }
}
