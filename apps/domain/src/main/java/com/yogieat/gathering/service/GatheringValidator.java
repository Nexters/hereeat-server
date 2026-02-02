package com.yogieat.gathering.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.command.GatheringCommand;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gathering 관련 검증 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class GatheringValidator {
    public void validateGatheringNotDeleted(Gathering gathering) {
        if (gathering.isDeleted()) {
            throw new CustomException(ErrorCode.GATHERING_DELETED);
        }
    }

    /**
     * Gathering 참여 인원이 가득 찼는지 검증
     *
     * @param gathering 검증할 모임
     * @param currentParticipantCount 현재 참여자 수
     * @throws CustomException GATHERING_FULL - 참여 인원이 가득 찼을 때
     */
    public void validateGatheringNotFull(Gathering gathering, long currentParticipantCount) {
        if (currentParticipantCount >= gathering.peopleCount()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }
    }

    /**
     * Gathering 생성 요청에 대한 사전 검증
     *
     * @param command 모임 생성 요청 DTO
     * @throws CustomException GATHERING_PEOPLE_COUNT_REQUIRED - 모임 인원 수가 null일 때
     * @throws CustomException GATHERING_PEOPLE_COUNT_OUT_OF_RANGE - 모임 인원 수가 허용 범위를 벗어났을 때
     * @throws CustomException GATHERING_SCHEDULED_DATE_REQUIRED - 모임 날짜가 null일 때
     * @throws CustomException GATHERING_SCHEDULED_DATE_PAST - 모임 날짜가 과거일 때
     */
    public void validateCreate(GatheringCommand.Create command) {
        validatePeopleCount(command.peopleCount());
        validateScheduledDate(command.scheduledDate());
    }

    /**
     * 모임 인원 수 검증
     *
     * @param peopleCount 모임 인원 수
     * @throws CustomException GATHERING_PEOPLE_COUNT_REQUIRED - 모임 인원 수가 null일 때
     * @throws CustomException GATHERING_PEOPLE_COUNT_OUT_OF_RANGE - 모임 인원 수가 허용 범위를 벗어났을 때
     */
    private void validatePeopleCount(Integer peopleCount) {
        if (peopleCount == null) {
            throw new CustomException(ErrorCode.GATHERING_PEOPLE_COUNT_REQUIRED);
        }

        if (peopleCount < 1 || peopleCount > 10) {
            throw new CustomException(ErrorCode.GATHERING_PEOPLE_COUNT_OUT_OF_RANGE);
        }
    }

    /**
     * 모임 날짜 검증
     *
     * @param scheduledDate 모임 예정 날짜
     * @throws CustomException GATHERING_SCHEDULED_DATE_REQUIRED - 모임 날짜가 null일 때
     * @throws CustomException GATHERING_SCHEDULED_DATE_PAST - 모임 날짜가 과거일 때
     */
    private void validateScheduledDate(LocalDate scheduledDate) {
        if (scheduledDate == null) {
            throw new CustomException(ErrorCode.GATHERING_SCHEDULED_DATE_REQUIRED);
        }

        if (scheduledDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.GATHERING_SCHEDULED_DATE_PAST);
        }
    }
}
