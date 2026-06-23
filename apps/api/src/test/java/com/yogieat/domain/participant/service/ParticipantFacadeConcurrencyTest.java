package com.yogieat.domain.participant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.DatabaseCleaner;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.service.GatheringRepository;
import com.yogieat.participant.domain.command.ParticipantCommand;
import com.yogieat.participant.service.ParticipantFacade;
import com.yogieat.participant.service.ParticipantRepository;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ParticipantFacadeConcurrencyTest {

    @Autowired private ParticipantFacade participantFacade;
    @Autowired private GatheringRepository gatheringRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private RegionJpaRepository regionJpaRepository;
    @Autowired private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clear(applicationContext);
        regionJpaRepository.save(RegionEntity.of(new RegionMaster(
                null,
                "GANGNAM",
                "서울",
                "강남역",
                new GeoJson.Point(List.of(127.0276, 37.4979)),
                RegionStatus.ACTIVE,
                1,
                null,
                null
        )));
    }

    @AfterEach
    void cleanup() {
        DatabaseCleaner.clear(applicationContext);
    }

    @Test
    @DisplayName("동시에 10명이 참여 시도 시 peopleCount(4)를 초과하지 않는다")
    void concurrentParticipation_shouldNotExceedPeopleCount() throws InterruptedException {
        // Given: peopleCount=4인 모임 생성
        Gathering gathering = gatheringRepository.save(
                new Gathering(
                        null,
                        "test-access-key",
                        "Test Gathering",
                        LocalDate.now().plusDays(7),
                        TimeSlot.LUNCH,
                        Region.fromString("GANGNAM"),
                        4,
                        null,
                        null,
                        null
                )
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 참여 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            String nickname = "참여자" + (char) ('A' + index);
                            ParticipantCommand.Create command =
                                    new ParticipantCommand.Create(
                                            gathering.accessKey(), nickname, null, List.of(), List.of());
                            participantFacade.participate(command);
                            successCount.incrementAndGet();
                        } catch (CustomException e) {
                            if (e.getErrorCode() == ErrorCode.GATHERING_FULL
                                    || e.getErrorCode() == ErrorCode.RECOMMEND_ALREADY_PROCEEDED) {
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

        long actualCount = participantRepository.countByGatheringId(gathering.id());
        assertThat(actualCount).isEqualTo(4);
    }

    @Test
    @DisplayName("서로 다른 모임은 동시 참여 가능 (락 독립성)")
    void differentGatherings_canParticipateSimultaneously() throws InterruptedException {
        // Given: 2개 모임 (각각 고유한 accessKey)
        Gathering gathering1 = createGatheringWithAccessKey("access-key-1", "Gathering 1");
        Gathering gathering2 = createGatheringWithAccessKey("access-key-2", "Gathering 2");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // When: 동시에 다른 모임 참여
        new Thread(
                () -> {
                    try {
                        startLatch.await();
                        participantFacade.participate(
                                new ParticipantCommand.Create(gathering1.accessKey(), "참여자A", null, List.of(), List.of()));
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errors.add(e);
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                })
                .start();

        new Thread(
                () -> {
                    try {
                        startLatch.await();
                        participantFacade.participate(
                                new ParticipantCommand.Create(gathering2.accessKey(), "참여자B", null, List.of(), List.of()));
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errors.add(e);
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                })
                .start();

        startLatch.countDown(); // 동시 시작
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);

        // Then: 둘 다 성공
        assertThat(completed)
                .withFailMessage("스레드가 제한 시간 내에 완료되지 않음")
                .isTrue();
        assertThat(errors)
                .withFailMessage(() -> "스레드 실행 중 예외 발생: " + errors)
                .isEmpty();
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(participantRepository.countByGatheringId(gathering1.id())).isEqualTo(1);
        assertThat(participantRepository.countByGatheringId(gathering2.id())).isEqualTo(1);
    }

    private Gathering createGatheringWithAccessKey(String accessKey, String title) {
        return gatheringRepository.save(
                new Gathering(
                        null,
                        accessKey,
                        title,
                        LocalDate.now().plusDays(7),
                        TimeSlot.LUNCH,
                        Region.fromString("GANGNAM"),
                        4,
                        null,
                        null,
                        null
                )
        );
    }
}
