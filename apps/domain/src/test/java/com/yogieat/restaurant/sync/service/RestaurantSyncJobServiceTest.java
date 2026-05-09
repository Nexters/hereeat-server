package com.yogieat.restaurant.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncJob;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncJobStatus;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncScope;
import com.yogieat.restaurant.sync.domain.value.RestaurantSyncTriggerType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RestaurantSyncJobServiceTest {

    @Mock
    private RestaurantSyncJobRepository syncJobRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantSyncJobService syncJobService;

    private void setDefaults() {
        ReflectionTestUtils.setField(syncJobService, "chunkSize", 50);
        ReflectionTestUtils.setField(syncJobService, "parallelism", 4);
    }

    @Test
    void createAllJob_whenExistingPendingJob_throwsConflict() {
        setDefaults();
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.RUNNING)).thenReturn(false);
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> syncJobService.createAllJob(RestaurantSyncTriggerType.MANUAL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYNC_JOB_CONFLICT);
    }

    @Test
    void createSingleJob_whenRestaurantNotFound_throwsNotFound() {
        setDefaults();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> syncJobService.createSingleJob(1L, RestaurantSyncTriggerType.MANUAL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void createSingleJob_success() {
        setDefaults();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(new Restaurant(
                1L,
                "ext",
                1L,
                "name",
                "address",
                4.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        )));
        when(syncJobRepository.existsByTargetRestaurantIdAndStatus(1L, RestaurantSyncJobStatus.RUNNING)).thenReturn(false);
        when(syncJobRepository.existsByTargetRestaurantIdAndStatus(1L, RestaurantSyncJobStatus.PENDING)).thenReturn(false);
        when(syncJobRepository.save(any())).thenAnswer(invocation -> {
            RestaurantSyncJob job = invocation.getArgument(0);
            return new RestaurantSyncJob(
                    10L,
                    job.scope(),
                    job.triggerType(),
                    job.targetRestaurantId(),
                    job.status(),
                    job.chunkSize(),
                    job.parallelism(),
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        });

        RestaurantSyncJob created = syncJobService.createSingleJob(1L, RestaurantSyncTriggerType.MANUAL);

        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.scope()).isEqualTo(RestaurantSyncScope.SINGLE);
        assertThat(created.status()).isEqualTo(RestaurantSyncJobStatus.PENDING);
    }

    @Test
    void createAllJob_whenUniqueConstraintViolation_throwsConflict() {
        setDefaults();
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.RUNNING)).thenReturn(false);
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.PENDING)).thenReturn(false);
        when(syncJobRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate active job"));

        assertThatThrownBy(() -> syncJobService.createAllJob(RestaurantSyncTriggerType.MANUAL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYNC_JOB_CONFLICT);
    }

    @Test
    void createAllJob_whenInvalidConfig_sanitizesChunkSizeAndParallelism() {
        ReflectionTestUtils.setField(syncJobService, "chunkSize", 0);
        ReflectionTestUtils.setField(syncJobService, "parallelism", -1);
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.RUNNING)).thenReturn(false);
        when(syncJobRepository.existsByScopeAndStatus(RestaurantSyncScope.ALL, RestaurantSyncJobStatus.PENDING)).thenReturn(false);
        when(syncJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantSyncJob created = syncJobService.createAllJob(RestaurantSyncTriggerType.MANUAL);

        assertThat(created.chunkSize()).isEqualTo(1);
        assertThat(created.parallelism()).isEqualTo(1);
    }

    @Test
    void createSingleJob_whenUniqueConstraintViolation_throwsConflict() {
        setDefaults();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(new Restaurant(
                1L,
                "ext",
                1L,
                "name",
                "address",
                4.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        )));
        when(syncJobRepository.existsByTargetRestaurantIdAndStatus(1L, RestaurantSyncJobStatus.RUNNING)).thenReturn(false);
        when(syncJobRepository.existsByTargetRestaurantIdAndStatus(1L, RestaurantSyncJobStatus.PENDING)).thenReturn(false);
        when(syncJobRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate active job"));

        assertThatThrownBy(() -> syncJobService.createSingleJob(1L, RestaurantSyncTriggerType.MANUAL))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SYNC_JOB_CONFLICT);
    }
}
