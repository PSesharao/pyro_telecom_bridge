package com.telecombridge.gateway.diameter;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Hop-by-Hop ID Uniqueness (Property 5).
 * <p>
 * **Validates: Requirements 2.2**
 * <p>
 * For any sequence of N generated Hop-by-Hop IDs on a single connection,
 * all N values SHALL be distinct (no duplicates). Also verifies End-to-End ID uniqueness.
 */
@Tag("Feature: telecom-bridge, Property 5: Hop-by-Hop ID Uniqueness")
class HopByHopIdUniquenessPropertyTest {

    /**
     * **Validates: Requirements 2.2**
     * <p>
     * For any sequence of N generated Hop-by-Hop IDs (N=1000+), all values are distinct.
     */
    @Property(tries = 100)
    void allHopByHopIdsAreDistinct(@ForAll @IntRange(min = 1000, max = 2000) int count) {
        IdGenerator generator = new IdGenerator();
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < count; i++) {
            long id = generator.nextHopByHopId();
            boolean added = ids.add(id);
            assertTrue(added,
                    "Duplicate Hop-by-Hop ID detected: " + id + " at iteration " + i + " of " + count);
        }

        assertEquals(count, ids.size(),
                "Expected " + count + " unique Hop-by-Hop IDs but got " + ids.size());
    }

    /**
     * **Validates: Requirements 2.2**
     * <p>
     * For any sequence of N generated End-to-End IDs (N=1000+), all values are distinct.
     */
    @Property(tries = 100)
    void allEndToEndIdsAreDistinct(@ForAll @IntRange(min = 1000, max = 2000) int count) {
        IdGenerator generator = new IdGenerator();
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < count; i++) {
            long id = generator.nextEndToEndId();
            boolean added = ids.add(id);
            assertTrue(added,
                    "Duplicate End-to-End ID detected: " + id + " at iteration " + i + " of " + count);
        }

        assertEquals(count, ids.size(),
                "Expected " + count + " unique End-to-End IDs but got " + ids.size());
    }
}
