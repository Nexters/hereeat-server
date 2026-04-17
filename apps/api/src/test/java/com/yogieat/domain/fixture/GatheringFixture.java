package com.yogieat.domain.fixture;

import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.gathering.GatheringEntity;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import java.time.LocalDate;

/** Test fixture for creating GatheringEntity instances */
public class GatheringFixture {

    /**
     * GatheringEntity 인스턴스를 생성합니다 (테스트용)
     *
     * @param title 모임 제목
     * @param peopleCount 모임 인원
     * @return GatheringEntity 인스턴스
     */
    public static GatheringEntity create(String title, int peopleCount) {
        return create(
                "test-access-key",
                title,
                LocalDate.now().plusDays(7),
                TimeSlot.LUNCH,
                Region.GANGNAM,
                peopleCount);
    }

    /**
     * GatheringEntity 인스턴스를 생성합니다 (전체 필드 지정)
     *
     * @param accessKey 접근 키
     * @param title 모임 제목
     * @param scheduledDate 예정일
     * @param timeSlot 시간대
     * @param region 장소
     * @param peopleCount 모임 인원
     * @return GatheringEntity 인스턴스
     */
    public static GatheringEntity create(
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Region region,
            int peopleCount) {
        return GatheringEntity.from(
                new Gathering(
                        null,
                        accessKey,
                        title,
                        scheduledDate,
                        timeSlot,
                        region,
                        peopleCount,
                        null,
                        null,
                        null
                )
        );
    }
}
