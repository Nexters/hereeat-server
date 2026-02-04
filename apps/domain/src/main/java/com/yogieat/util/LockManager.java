package com.yogieat.util;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 엔티티별 동시성 제어를 위한 범용 Lock Manager
 * ConcurrentHashMap을 사용하여 각 엔티티 ID(Long) 또는 키(String)마다 독립적인 ReentrantLock을 관리합니다.
 * 사용 예시:
 * lockManager.executeWithLock(entityId, () -> {
 *     // 동시성 제어가 필요한 로직
 *     return result;
 * });
 * lockManager.executeWithLock(accessKey, () -> {
 *     // String 키로 동시성 제어
 *     return result;
 * });
 */
@Component
@Slf4j
public class LockManager {

    private static final long LOCK_TIMEOUT_SECONDS = 5L;

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> stringLocks = new ConcurrentHashMap<>();

    /**
     * 특정 엔티티에 대한 락 획득
     *
     * @param entityId 엔티티 ID
     * @return 해당 엔티티의 ReentrantLock (없으면 생성)
     */
    public ReentrantLock getLock(Long entityId) {
        return locks.computeIfAbsent(
                entityId,
                id -> {
                    log.debug("Creating new lock for entity: {}", id);
                    return new ReentrantLock(true); // fair=true (FIFO 순서 보장)
                });
    }

    /**
     * 특정 키(String)에 대한 락 획득
     *
     * @param key 락 키 (예: accessKey)
     * @return 해당 키의 ReentrantLock (없으면 생성)
     */
    public ReentrantLock getLock(String key) {
        return stringLocks.computeIfAbsent(
                key,
                k -> {
                    log.debug("Creating new lock for key: {}", k);
                    return new ReentrantLock(true); // fair=true (FIFO 순서 보장)
                });
    }

    /**
     * 락을 사용하여 작업 실행 (자동 락 획득/해제, 타임아웃 적용)
     *
     * @param entityId 엔티티 ID
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 실행 결과
     * @throws CustomException 락 획득 타임아웃 시
     */
    public <T> T executeWithLock(Long entityId, Task<T> task) {
        ReentrantLock lock = getLock(entityId);

        log.debug("Attempting to acquire lock for entity: {} with timeout: {}s", entityId, LOCK_TIMEOUT_SECONDS);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire lock for entity: {} within {}s", entityId, LOCK_TIMEOUT_SECONDS);
                throw new CustomException(ErrorCode.LOCK_TIMEOUT);
            }

            log.debug("Lock acquired for entity: {}", entityId);
            return task.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for entity: {}", entityId);
            throw new CustomException(ErrorCode.LOCK_TIMEOUT);
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Lock released for entity: {}", entityId);
            }
        }
    }

    /**
     * 락을 사용하여 작업 실행 (자동 락 획득/해제, 타임아웃 적용) - String 키 버전
     *
     * @param key 락 키 (예: accessKey)
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 실행 결과
     * @throws CustomException 락 획득 타임아웃 시
     */
    public <T> T executeWithLock(String key, Task<T> task) {
        ReentrantLock lock = getLock(key);

        log.debug("Attempting to acquire lock for key: {} with timeout: {}s", key, LOCK_TIMEOUT_SECONDS);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire lock for key: {} within {}s", key, LOCK_TIMEOUT_SECONDS);
                throw new CustomException(ErrorCode.LOCK_TIMEOUT);
            }

            log.debug("Lock acquired for key: {}", key);
            return task.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for key: {}", key);
            throw new CustomException(ErrorCode.LOCK_TIMEOUT);
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Lock released for key: {}", key);
            }
        }
    }

    /** 작업 실행 인터페이스 (함수형 인터페이스) */
    @FunctionalInterface
    public interface Task<T> {
        T execute();
    }

    /**
     * 메모리 누수 방지: 삭제된 엔티티의 락 정리 (선택적) 스케줄러로 주기적으로 호출하거나, 엔티티 삭제 시 호출
     * @param entityId 삭제된 엔티티 ID
     */
    public void removeLock(Long entityId) {
        locks.remove(entityId);
        log.debug("Removed lock for entity: {}", entityId);
    }

    /**
     * 메모리 누수 방지: 삭제된 키의 락 정리
     * @param key 삭제할 락 키
     */
    public void removeLock(String key) {
        stringLocks.remove(key);
        log.debug("Removed lock for key: {}", key);
    }

    /**
     * 현재 관리 중인 락 개수 (모니터링용)
     * @return 현재 관리 중인 락의 개수 (Long 키 + String 키 합계)
     */
    public int getLockCount() {
        return locks.size() + stringLocks.size();
    }
}
