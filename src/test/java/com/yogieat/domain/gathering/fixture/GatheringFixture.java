package com.yogieat.domain.gathering.fixture;

import com.yogieat.domain.common.Place;
import com.yogieat.domain.gathering.domain.value.TimeSlot;
import com.yogieat.domain.gathering.entity.GatheringEntity;
import java.lang.reflect.Constructor;
import java.time.LocalDate;

/** Test fixture for creating GatheringEntity instances */
public class GatheringFixture {

    /**
     * GatheringEntity 인스턴스를 생성합니다 (테스트용)
     *
     * @param title 모임 제목
     * @param headCount 모임 인원
     * @return GatheringEntity 인스턴스
     */
    public static GatheringEntity create(String title, int headCount) {
        return create(
                "test-access-key",
                title,
                LocalDate.now().plusDays(7),
                TimeSlot.LUNCH,
                Place.GANGNAM,
                headCount);
    }

    /**
     * GatheringEntity 인스턴스를 생성합니다 (전체 필드 지정)
     *
     * @param accessKey 접근 키
     * @param title 모임 제목
     * @param scheduledDate 예정일
     * @param timeSlot 시간대
     * @param place 장소
     * @param headCount 모임 인원
     * @return GatheringEntity 인스턴스
     */
    public static GatheringEntity create(
            String accessKey,
            String title,
            LocalDate scheduledDate,
            TimeSlot timeSlot,
            Place place,
            int headCount) {
        try {
            // Reflection을 사용하여 private 생성자 접근
            Constructor<GatheringEntity> constructor =
                    GatheringEntity.class.getDeclaredConstructor(
                            String.class,
                            String.class,
                            LocalDate.class,
                            TimeSlot.class,
                            Place.class,
                            int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(accessKey, title, scheduledDate, timeSlot, place, headCount);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GatheringEntity for testing", e);
        }
    }
}
