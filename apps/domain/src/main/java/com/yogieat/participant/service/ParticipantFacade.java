package com.yogieat.participant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.result.GatheringResult;
import com.yogieat.gathering.service.GatheringEventNotifier;
import com.yogieat.gathering.service.GatheringService;
import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.command.ParticipantCommand;
import com.yogieat.participant.domain.result.ParticipantResult;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.recommend.event.RecommendResultCreatedEvent;
import com.yogieat.recommend.service.RecommendResultService;
import com.yogieat.util.LockManager;
import com.yogieat.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantFacade {
    private final ParticipantService participantService;
    private final GatheringService gatheringService;
    private final LockManager lockManager;
    private final ApplicationEventPublisher eventPublisher;
    private final RecommendResultService recommendResultService;
    private final GatheringEventNotifier gatheringEventNotifier;

    @Transactional(readOnly = true)
    public void validateNickname(String accessKey, String nickname) {
        Gathering gathering = gatheringService.getGatheringByAccessKey(accessKey);
        if (participantService.existsByGatheringIdAndNickname(gathering.id(), nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Transactional
    public ParticipantResult.Create participate(ParticipantCommand.Create command) {
        // Gathering별 락을 사용하여 동시성 제어
        return lockManager.executeWithLock(
                command.accessKey(),
                () -> {
                    log.info("Processing participation for gathering: {}", command.accessKey());

                    // 1. Gathering 존재 여부 및 삭제 여부 검증 (accessKey 기반)
                    Gathering gathering =
                            gatheringService.getGatheringByAccessKey(command.accessKey());

                    // 1-1. 추천 시작 여부 검증 (추천이 이미 시작되었으면 참여 불가)
                    if (recommendResultService.existsByGatheringId(gathering.id())) {
                        throw new CustomException(ErrorCode.RECOMMEND_ALREADY_PROCEEDED);
                    }

                    // 2. 현재 참여자 수 조회 (락으로 보호됨)
                    long currentParticipantCount =
                            participantService.countByGatheringId(gathering.id());

                    // 3. Gathering 참여 인원 초과 검증
                    gatheringService.validateGatheringNotFull(gathering, currentParticipantCount);

                    // 3-1. 같은 모임 내 닉네임 중복 검증
                    if (command.nickname() != null &&
                            participantService.existsByGatheringIdAndNickname(
                                    gathering.id(), command.nickname())) {
                        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
                    }

                    // 4. Double distance를 DistanceRange로 변환 (null이면 ANY)
                    DistanceRange distanceRange = DistanceRange.fromDistance(command.distance());

                    // 5. List<String>을 콤마로 구분된 String으로 변환
                    String preferences = StringUtils.joinWithComma(command.preferences());
                    String dislikes = StringUtils.joinWithComma(command.dislikes());

                    // 6. 참여자 생성 및 저장
                    Participant participant =
                            participantService.create(
                                    gathering.id(), command.nickname(), distanceRange, preferences, dislikes);

                    // 7. 참여자 변경 SSE 알림
                    long newCount = currentParticipantCount + 1;
                    GatheringResult.ParticipantCount status =
                            GatheringResult.ParticipantCount.of(newCount, gathering.peopleCount());
                    gatheringEventNotifier.notifyParticipantJoined(command.accessKey(), status);

                    // 8. 인원 충족 시 PENDING 상태 생성 및 이벤트 발행
                    if (newCount == gathering.peopleCount()) {
                        log.info("Gathering is full. Creating PENDING status for gathering: {}", gathering.id());

                        // PENDING 레코드 생성 (동기적)
                        recommendResultService.createPendingStatus(gathering.id());

                        log.info("Publishing RecommendResultCreatedEvent for gathering: {}", gathering.id());
                        eventPublisher.publishEvent(RecommendResultCreatedEvent.of(
                                this,
                                gathering.id(),
                                gathering.region(),
                                gathering.peopleCount(),
                                command.accessKey(),
                                newCount
                        ));
                    }

                    log.info("Successfully participated in gathering: {}", gathering.id());

                    return ParticipantResult.Create.of(gathering, participant);
                });
    }
}
