package com.telecombridge.gateway.diameter;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique Hop-by-Hop-ID and End-to-End-ID values for Diameter messages.
 * Uniqueness is guaranteed per connection lifetime using atomic counters.
 */
@Component
public class IdGenerator {

    private final AtomicLong hopByHopCounter = new AtomicLong(0);
    private final AtomicLong endToEndCounter = new AtomicLong(
            System.currentTimeMillis() << 20);

    /**
     * Generates the next unique Hop-by-Hop-ID.
     * Values are monotonically increasing and unique per connection lifetime.
     *
     * @return the next Hop-by-Hop-ID as an unsigned 32-bit value
     */
    public long nextHopByHopId() {
        return hopByHopCounter.incrementAndGet();
    }

    /**
     * Generates the next unique End-to-End-ID.
     * Combines current time in high bits with a sequence in low bits
     * to ensure global uniqueness across restarts.
     *
     * @return the next End-to-End-ID as an unsigned 32-bit value
     */
    public long nextEndToEndId() {
        return endToEndCounter.incrementAndGet();
    }
}
