package com.yogieat.restaurant.sync.service;

import com.yogieat.restaurant.service.RestaurantRepository;
import com.yogieat.restaurant.sync.domain.RestaurantSyncPatchCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantSyncChunkPersistenceService {

    private static final int DB_BATCH_SIZE = 300;

    private final RestaurantRepository restaurantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persistChunkChanges(
            List<RestaurantSyncPatchCommand> patchCommands,
            List<Long> deleteIds,
            List<String> errorMessages
    ) {
        int successCount = 0;

        if (!patchCommands.isEmpty()) {
            for (int start = 0; start < patchCommands.size(); start += DB_BATCH_SIZE) {
                int end = Math.min(start + DB_BATCH_SIZE, patchCommands.size());
                List<RestaurantSyncPatchCommand> batch = patchCommands.subList(start, end);
                try {
                    long startedAt = System.nanoTime();
                    restaurantRepository.batchApplySyncPatch(batch);
                    successCount += batch.size();
                    long dbMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    log.debug("batchApplySyncPatch completed. size={} tookMs={}", batch.size(), dbMs);
                } catch (Exception e) {
                    log.error("Batch sync patch failed for {} restaurants", batch.size(), e);
                    if (errorMessages.size() < 10) {
                        errorMessages.add("batch update failed: " + e.getMessage());
                    }
                }
            }
        }

        if (!deleteIds.isEmpty()) {
            for (int start = 0; start < deleteIds.size(); start += DB_BATCH_SIZE) {
                int end = Math.min(start + DB_BATCH_SIZE, deleteIds.size());
                List<Long> batch = deleteIds.subList(start, end);
                try {
                    long startedAt = System.nanoTime();
                    restaurantRepository.batchDeleteByIds(batch);
                    successCount += batch.size();
                    long dbMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    log.debug("batchDeleteByIds completed. size={} tookMs={}", batch.size(), dbMs);
                } catch (Exception e) {
                    log.error("Batch delete failed for {} restaurants", batch.size(), e);
                    if (errorMessages.size() < 10) {
                        errorMessages.add("batch delete failed: " + e.getMessage());
                    }
                }
            }
        }

        return successCount;
    }
}
