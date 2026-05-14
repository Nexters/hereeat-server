package com.yogieat.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LockManagerTest {

    @Test
    void executeWithLock_ShouldRemoveStringLock_WhenLockIsIdleAfterTask() {
        LockManager lockManager = new LockManager();

        String result = lockManager.executeWithLock("restaurant:create:external", () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(lockManager.getLockCount()).isZero();
    }
}
