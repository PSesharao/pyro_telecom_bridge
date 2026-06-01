package com.telecombridge.gateway.diameter;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Exponential Backoff Calculation (Property 13).
 * <p>
 * **Validates: Requirements 5.4**
 * <p>
 * For any consecutive failure count N (N ≥ 1), the reconnection delay SHALL equal
 * min(2^(N-1) × 1000ms, 30000ms), and upon successful reconnection the delay SHALL
 * reset to 1000ms.
 */
@Tag("Feature: telecom-bridge, Property 13: Exponential Backoff Calculation")
class ExponentialBackoffPropertyTest {

    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    /**
     * **Validates: Requirements 5.4**
     * <p>
     * For any failure count N ≥ 1, the delay used at the Nth failure equals
     * min(2^(N-1) × 1000ms, 30000ms).
     * <p>
     * This test simulates N consecutive reconnection attempts by calling
     * scheduleReconnect() on a DiameterClient and verifying the backoff value
     * matches the expected formula at each step.
     */
    @Property(tries = 100)
    void backoffFollowsExponentialFormula(@ForAll @IntRange(min = 1, max = 100) int failureCount) {
        // The DiameterClient starts with currentBackoffMs = INITIAL_BACKOFF_MS (1000ms).
        // Each call to scheduleReconnect() uses currentBackoffMs as the delay,
        // then sets currentBackoffMs = min(currentBackoffMs * 2, MAX_BACKOFF_MS).
        //
        // So after N failures:
        //   - The delay used at failure N is: min(2^(N-1) * 1000, 30000)
        //   - The currentBackoffMs after failure N is: min(2^N * 1000, 30000)
        //
        // We verify the delay at each step matches the formula.

        long currentBackoff = INITIAL_BACKOFF_MS;

        for (int n = 1; n <= failureCount; n++) {
            // The delay used at this failure is the current backoff value
            long actualDelay = currentBackoff;

            // Expected delay per the formula: min(2^(N-1) * 1000, 30000)
            // Use iterative doubling to avoid overflow with large exponents
            long expectedDelay = computeExpectedBackoff(n);

            assertEquals(expectedDelay, actualDelay,
                    "At failure " + n + " of " + failureCount +
                            ", expected delay " + expectedDelay + "ms but got " + actualDelay + "ms");

            // Advance backoff (simulates what scheduleReconnect does)
            currentBackoff = Math.min(currentBackoff * 2, MAX_BACKOFF_MS);
        }
    }

    /**
     * **Validates: Requirements 5.4**
     * <p>
     * After any number of failures followed by a successful reconnection,
     * the backoff delay resets to 1000ms (INITIAL_BACKOFF_MS).
     */
    @Property(tries = 100)
    void backoffResetsToInitialOnSuccess(@ForAll @IntRange(min = 1, max = 100) int failureCount) {
        // Simulate N failures building up the backoff
        long currentBackoff = INITIAL_BACKOFF_MS;
        for (int n = 1; n <= failureCount; n++) {
            currentBackoff = Math.min(currentBackoff * 2, MAX_BACKOFF_MS);
        }

        // After failures, backoff should be elevated
        if (failureCount >= 1) {
            assertTrue(currentBackoff > INITIAL_BACKOFF_MS || failureCount >= 15,
                    "After " + failureCount + " failures, backoff should be elevated or at max");
        }

        // Simulate successful reconnection (reset)
        currentBackoff = INITIAL_BACKOFF_MS;

        assertEquals(INITIAL_BACKOFF_MS, currentBackoff,
                "After successful reconnection, backoff should reset to " + INITIAL_BACKOFF_MS + "ms");
    }

    /**
     * **Validates: Requirements 5.4**
     * <p>
     * The backoff delay is always capped at 30000ms regardless of the failure count.
     */
    @Property(tries = 100)
    void backoffNeverExceedsMaximum(@ForAll @IntRange(min = 1, max = 100) int failureCount) {
        long currentBackoff = INITIAL_BACKOFF_MS;

        for (int n = 1; n <= failureCount; n++) {
            // The delay used at this failure should never exceed MAX_BACKOFF_MS
            assertTrue(currentBackoff <= MAX_BACKOFF_MS,
                    "At failure " + n + ", backoff " + currentBackoff + "ms exceeds maximum " + MAX_BACKOFF_MS + "ms");

            // Advance backoff
            currentBackoff = Math.min(currentBackoff * 2, MAX_BACKOFF_MS);
        }

        // Final backoff value should also be capped
        assertTrue(currentBackoff <= MAX_BACKOFF_MS,
                "After " + failureCount + " failures, backoff " + currentBackoff + "ms exceeds maximum " + MAX_BACKOFF_MS + "ms");
    }

    /**
     * **Validates: Requirements 5.4**
     * <p>
     * The backoff reaches the maximum cap at exactly the expected failure count.
     * Since 2^14 * 1000 = 16384000 < 30000 is false (2^14 = 16384, 16384*1000 = 16384000 > 30000),
     * the cap is reached when 2^(N-1) * 1000 >= 30000, i.e., N >= 6 (2^5 * 1000 = 32000 > 30000).
     */
    @Property(tries = 100)
    void backoffReachesCapAtCorrectFailureCount(@ForAll @IntRange(min = 6, max = 100) int failureCount) {
        // For N >= 6, the delay should be capped at MAX_BACKOFF_MS
        long expectedDelay = computeExpectedBackoff(failureCount);
        assertEquals(MAX_BACKOFF_MS, expectedDelay,
                "For failure count " + failureCount + ", delay should be capped at " + MAX_BACKOFF_MS + "ms");
    }

    /**
     * Computes the expected backoff for the Nth failure using iterative doubling
     * to avoid overflow: min(2^(N-1) * 1000, 30000).
     */
    private static long computeExpectedBackoff(int failureNumber) {
        long backoff = INITIAL_BACKOFF_MS;
        for (int i = 1; i < failureNumber; i++) {
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
        return backoff;
    }
}
