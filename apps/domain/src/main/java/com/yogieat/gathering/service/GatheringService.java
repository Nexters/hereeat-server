package com.yogieat.gathering.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.command.GatheringCommand;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.participant.service.ParticipantService;
import java.util.List;
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
     * @param command 모임 생성 요청
     * @return 생성된 Gathering
     */
    @Transactional
    public Gathering create(GatheringCommand.Create command) {
        gatheringValidator.validateCreate(command);

        Gathering gathering = new Gathering(
                null,
                createAccessKey(),
                null,
                command.scheduledDate(),
                command.timeSlot(),
                command.region(),
                command.peopleCount(),
                null,
                null,
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
    public GatheringResult.ParticipantCount getGatheringParticipantStatus(String accessKey) {
        Gathering gathering = getGatheringByAccessKey(accessKey);

        long currentCount = participantService.countByGatheringId(gathering.id());
        return new GatheringResult.ParticipantCount(
                currentCount,
                gathering.peopleCount()
        );
    }

    @Transactional(readOnly = true)
    public Gathering getGatheringBy(Long id) {
        Gathering gathering = gatheringRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        gatheringValidator.validateGatheringNotDeleted(gathering);
        return gathering;
    }

    @Transactional(readOnly = true)
    public List<Gathering> findAdminGatherings(
            GatheringAdminListCriteria criteria,
            int page,
            int size
    ) {
        return gatheringRepository.findAdminGatherings(criteria, page, size);
    }

    @Transactional(readOnly = true)
    public long countAdminGatherings(GatheringAdminListCriteria criteria) {
        return gatheringRepository.countAdminGatherings(criteria);
    }

    @Transactional(readOnly = true)
    public List<Gathering> findAdminGatherings(GatheringAdminListCriteria criteria) {
        return gatheringRepository.findAdminGatherings(criteria);
    }

    private String createAccessKey() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
    }
}
