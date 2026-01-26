package com.yogieat.domain.gathering.service;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.gathering.controller.response.CreateGatheringResponse;
import com.yogieat.domain.gathering.domain.Gathering;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatheringService {
    private final GatheringValidator gatheringValidator;
    private final GatheringRepository gatheringRepository;

    /**
     * Gathering 존재 여부 및 삭제 여부 검증
     * GatheringValidator에게 위임
     *
     * @param gatheringId 검증할 모임 ID
     * @return 검증된 Gathering 도메인
     */
    public Gathering validateGatheringExists(Long gatheringId) {
        return gatheringValidator.validateGatheringExists(gatheringId);
    }

    /**
     * Gathering 존재 여부 및 삭제 여부 검증 (accessKey 기반)
     * GatheringValidator에게 위임
     *
     * @param accessKey 검증할 모임 접근 키
     * @return 검증된 Gathering 도메인
     */
    public Gathering validateGatheringExistsByAccessKey(String accessKey) {
        return gatheringValidator.validateGatheringExistsByAccessKey(accessKey);
    }

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

    @Transactional
    public CreateGatheringResponse createGathering(CreateGatheringRequest request) {
        gatheringValidator.validateCreate(request);

        String accessKey = createAccessKey();
        Gathering gathering = new Gathering(
                null,
                accessKey,
                null,
                request.scheduledDate(),
                request.timeSlot(),
                request.region(),
                request.peopleCount(),
                null
        );

        gatheringRepository.save(gathering);
        return new CreateGatheringResponse(accessKey);
    }

    private String createAccessKey() {
        return UUID.randomUUID()
                .toString().
                replace("-", "")
                .substring(0, 12);
    }

}
