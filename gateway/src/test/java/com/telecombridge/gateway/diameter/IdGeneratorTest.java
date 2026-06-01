package com.telecombridge.gateway.diameter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void nextHopByHopId_returnsMonotonicallyIncreasingValues() {
        IdGenerator generator = new IdGenerator();

        long first = generator.nextHopByHopId();
        long second = generator.nextHopByHopId();
        long third = generator.nextHopByHopId();

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(third).isEqualTo(3);
    }

    @Test
    void nextHopByHopId_generatesUniqueValues() {
        IdGenerator generator = new IdGenerator();
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(generator.nextHopByHopId());
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    void nextEndToEndId_generatesUniqueValues() {
        IdGenerator generator = new IdGenerator();
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(generator.nextEndToEndId());
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    void nextEndToEndId_startsWithTimeBasedSeed() {
        long beforeCreation = System.currentTimeMillis() << 20;
        IdGenerator generator = new IdGenerator();
        long afterCreation = System.currentTimeMillis() << 20;

        long firstId = generator.nextEndToEndId();

        // The first ID should be greater than the time-based seed at creation
        assertThat(firstId).isGreaterThan(beforeCreation);
        // And within a reasonable range of the after-creation time
        assertThat(firstId).isLessThanOrEqualTo(afterCreation + 1);
    }
}
