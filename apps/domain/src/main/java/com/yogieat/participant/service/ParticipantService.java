package com.yogieat.participant.service;

import com.yogieat.participant.domain.Participant;
import com.yogieat.participant.domain.value.DistanceRange;
import com.yogieat.participant.domain.value.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantService {
    private final ParticipantRepository participantRepository;

    @Transactional
    public Participant create(
            Long gatheringId,
            String nickname,
            DistanceRange distanceRange,
            String preferences,
            String dislikes
    ) {
        Participant participant = new Participant(
                null, // id는 저장 시 자동 생성
                null, // 추후 인증 추가 시 userId로 설정
                gatheringId,
                nickname,
                distanceRange,
                preferences,
                dislikes,
                Role.MEMBER, // 참여자는 기본적으로 MEMBER 역할
                null,
                null
        );
        return participantRepository.save(participant);
    }

    @Transactional(readOnly = true)
    public boolean existsByGatheringIdAndNickname(Long gatheringId, String nickname) {
        return participantRepository.existsByGatheringIdAndNickname(gatheringId, nickname);
    }

    /**
     * 특정 모임의 현재 참여자 수 조회
     *
     * @param gatheringId 모임 ID
     * @return 참여자 수
     */
    @Transactional(readOnly = true)
    public long countByGatheringId(Long gatheringId) {
        return participantRepository.countByGatheringId(gatheringId);
    }


    /**
     * 특정 모임의 참여자 목록 조회
     *
     * @param gatheringId 모임 ID
     * @return 참여자 목록
     */
    @Transactional(readOnly = true)
    public List<Participant> getByGatheringId(Long gatheringId) {
        return participantRepository.findByGatheringId(gatheringId);
    }

    @Transactional(readOnly = true)
    public List<Participant> getByGatheringIds(List<Long> gatheringIds) {
        return participantRepository.findByGatheringIds(gatheringIds);
    }
}
