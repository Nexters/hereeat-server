package com.yogieat.domain.participant.service;

import com.yogieat.domain.gathering.controller.request.CreateGatheringRequest;
import com.yogieat.domain.participant.domain.Participant;
import com.yogieat.domain.participant.domain.value.DistanceRange;
import com.yogieat.domain.participant.domain.value.Role;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipantService {
    private final ParticipantRepository participantRepository;

    public Participant create(
            Long gatheringId,
            DistanceRange distanceRange,
            String preferences,
            String dislikes
    ) {
        Participant participant = new Participant(
                null, // id는 저장 시 자동 생성
                null, // 추후 인증 추가 시 userId로 설정
                gatheringId,
                distanceRange,
                preferences,
                dislikes,
                Role.MEMBER // 참여자는 기본적으로 MEMBER 역할
        );
        return participantRepository.save(participant);
    }

    /**
     * 모임 생성자를 참여자로 등록
     * - 설문 전 단계이므로 설문 관련 필드는 null 허용
     *
     * @param request 모임 생성 요청
     * @param gatheringId 생성된 모임의 ID
     */
    @Transactional
    public void createHostParticipant(CreateGatheringRequest request, Long gatheringId) {
        Participant participant = new Participant(
                null, // id는 저장 시 자동 생성
                null, // 추후 인증 추가 시 userId로 설정
                gatheringId,
                null, // 설문 전
                null, // 설문 전
                null, // 설문 전
                Role.MEMBER // 임시로 MEMBER로 저장
        );

        participantRepository.save(participant);
    }

    /**
     * 특정 모임의 현재 참여자 수 조회
     *
     * @param gatheringId 모임 ID
     * @return 참여자 수
     */
    public long countByGatheringId(Long gatheringId) {
        return participantRepository.countByGatheringId(gatheringId);
    }
}
