package com.yogieat.testsupport;

/**
 * Test harness contract for reference/master data that must be rebuilt after {@link com.yogieat.DatabaseCleaner}.
 *
 * <p>Rule: if a feature seeds required DB state at application startup, tests that use DatabaseCleaner
 * must register a reset hook so the same state is restored after cleanup.</p>
 */
public interface DatabaseResetHook {

    void reset();
}
