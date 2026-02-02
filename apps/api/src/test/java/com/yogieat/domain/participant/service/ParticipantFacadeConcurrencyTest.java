package com.yogieat.domain.participant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.datasource.db.core.gathering.GatheringEntity;
import com.yogieat.datasource.db.core.gathering.GatheringJpaRepository;
import com.yogieat.datasource.db.core.participant.ParticipantJpaRepository;
import com.yogieat.domain.fixture.GatheringFixture;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.participant.domain.command.ParticipantCommand;
import com.yogieat.participant.service.ParticipantFacade;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ParticipantFacadeConcurrencyTest {

    @Autowired private ParticipantFacade participantFacade;

    @Autowired private GatheringJpaRepository gatheringRepository;

    @Autowired private ParticipantJpaRepository participantRepository;

    @AfterEach
    void cleanup() {
        participantRepository.deleteAll();
        gatheringRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 10명이 참여 시도 시 peopleCount(4)를 초과하지 않는다")
    void concurrentParticipation_shouldNotExceedPeopleCount() throws InterruptedException {
        // Given: peopleCount=4인 모임 생성
        GatheringEntity gathering = GatheringFixture.create("Test Gathering", 4);
        gatheringRepository.save(gathering);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 참여 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            ParticipantCommand.Create command =
                                    new ParticipantCommand.Create(
                                            gathering.getAccessKey(), null, List.of(), List.of());
                            participantFacade.participate(command);
                            successCount.incrementAndGet();
                        } catch (CustomException e) {
                            if (e.getErrorCode() == ErrorCode.GATHERING_FULL) {
                                failCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 정확히 4명 성공, 6명 실패
        assertThat(successCount.get()).isEqualTo(4);
        assertThat(failCount.get()).isEqualTo(6);

        long actualCount = participantRepository.countByGatheringId(gathering.getId());
        assertThat(actualCount).isEqualTo(4);
    }

    @Test
    @DisplayName("서로 다른 모임은 동시 참여 가능 (락 독립성)")
    void differentGatherings_canParticipateSimultaneously() throws InterruptedException {
        // Given: 2개 모임 (각각 고유한 accessKey)
        GatheringEntity gathering1 = createGatheringWithAccessKey("access-key-1", "Gathering 1");
        GatheringEntity gathering2 = createGatheringWithAccessKey("access-key-2", "Gathering 2");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        List<Long> executionTimes = new CopyOnWriteArrayList<>();

        // When: 동시에 다른 모임 참여
        new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                long start = System.currentTimeMillis();
                                participantFacade.participate(createCommand(gathering1.getAccessKey()));
                                executionTimes.add(System.currentTimeMillis() - start);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                endLatch.countDown();
                            }
                        })
                .start();

        new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                long start = System.currentTimeMillis();
                                participantFacade.participate(createCommand(gathering2.getAccessKey()));
                                executionTimes.add(System.currentTimeMillis() - start);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                endLatch.countDown();
                            }
                        })
                .start();

        startLatch.countDown(); // 동시 시작
        endLatch.await(5, TimeUnit.SECONDS);

        // Then: 둘 다 성공, 실행 시간이 거의 동시 (락 대기 없음)
        assertThat(executionTimes).hasSize(2);
        assertThat(participantRepository.countByGatheringId(gathering1.getId())).isEqualTo(1);
        assertThat(participantRepository.countByGatheringId(gathering2.getId())).isEqualTo(1);
    }

    private GatheringEntity createGathering(String title) {
        GatheringEntity gathering = GatheringFixture.create(title, 4);
        return gatheringRepository.save(gathering);
    }

    private GatheringEntity createGatheringWithAccessKey(String accessKey, String title) {
        GatheringEntity gathering = GatheringFixture.create(
                accessKey,
                title,
                LocalDate.now().plusDays(7),
                TimeSlot.LUNCH,
                Region.GANGNAM,
                4
        );
        return gatheringRepository.save(gathering);
    }

    private ParticipantCommand.Create createCommand(String accessKey) {
        return new ParticipantCommand.Create(accessKey, null, List.of(), List.of());
    }
}
