package com.telecombridge.gateway.diameter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdGeneratorTest {

    @Test
    void generate_returnsCorrectFormat() {
        SessionIdGenerator generator = new SessionIdGenerator("CTOPUP");

        String sessionId = generator.generate();

        String[] parts = sessionId.split(";");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("CTOPUP");
        // Second part is nanoTime - should be a numeric string
        assertThat(parts[1]).matches("\\d+");
        // Third part is sequence - should be "1" for first call
        assertThat(parts[2]).isEqualTo("1");
    }

    @Test
    void generate_usesConfiguredOriginHost() {
        SessionIdGenerator generator = new SessionIdGenerator("MY_HOST");

        String sessionId = generator.generate();

        assertThat(sessionId).startsWith("MY_HOST;");
    }

    @Test
    void generate_incrementsSequence() {
        SessionIdGenerator generator = new SessionIdGenerator("CTOPUP");

        String first = generator.generate();
        String second = generator.generate();
        String third = generator.generate();

        assertThat(first.split(";")[2]).isEqualTo("1");
        assertThat(second.split(";")[2]).isEqualTo("2");
        assertThat(third.split(";")[2]).isEqualTo("3");
    }

    @Test
    void generate_producesUniqueSessionIds() {
        SessionIdGenerator generator = new SessionIdGenerator("CTOPUP");
        Set<String> sessionIds = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            sessionIds.add(generator.generate());
        }

        assertThat(sessionIds).hasSize(1000);
    }

    @Test
    void generate_matchesPcapTraceFormat() {
        // Per PCAP trace, format is "CTOPUP;{number};{sequence}"
        SessionIdGenerator generator = new SessionIdGenerator("CTOPUP");

        String sessionId = generator.generate();

        // Should match pattern: CTOPUP;digits;digits
        assertThat(sessionId).matches("CTOPUP;\\d+;\\d+");
    }
}
