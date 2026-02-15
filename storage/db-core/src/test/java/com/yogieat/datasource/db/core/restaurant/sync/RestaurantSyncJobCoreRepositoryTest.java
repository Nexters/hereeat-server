package com.yogieat.datasource.db.core.restaurant.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantSyncJobCoreRepositoryTest {

    @Mock
    private RestaurantSyncJobJpaRepository syncJobJpaRepository;

    @InjectMocks
    private RestaurantSyncJobCoreRepository coreRepository;

    @Test
    @DisplayName("claimNextPendingJob은 가장 오래된 PENDING Job을 RUNNING으로 점유한다")
    void claimNextPendingJob_ShouldReturnClaimedJob() {
        RestaurantSyncJobEntity entity = RestaurantSyncJobEntity.from(newJob());
        when(syncJobJpaRepository.claimNextPendingJobIds()).thenReturn(java.util.List.of(1L));
        when(syncJobJpaRepository.findById(1L)).thenReturn(Optional.of(entity));

        Optional<RestaurantSyncJob> claimed = coreRepository.claimNextPendingJob();

        assertThat(claimed).isPresent();
        verify(syncJobJpaRepository).claimNextPendingJobIds();
        verify(syncJobJpaRepository).findById(1L);
    }

    @Test
    @DisplayName("claim할 PENDING Job이 없으면 empty를 반환한다")
    void claimNextPendingJob_ShouldReturnEmpty_WhenNoPendingJob() {
        when(syncJobJpaRepository.claimNextPendingJobIds()).thenReturn(java.util.List.of());
        assertThat(coreRepository.claimNextPendingJob()).isEmpty();
    }

    @Test
    @DisplayName("엔티티가 없으면 상태 전이 메서드는 예외를 던진다")
    void statusTransition_ShouldThrowWhenJobMissing() {
        when(syncJobJpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coreRepository.markSuccess(99L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYNC_JOB_NOT_FOUND);
    }

    private RestaurantSyncJob newJob() {
        return RestaurantSyncJob.create(
                RestaurantSyncScope.ALL,
                RestaurantSyncTriggerType.MANUAL,
                null,
                50,
                4
        );
    }
}
