package com.telecombridge.gateway.diameter;

import com.telecombridge.codec.DiameterTimeoutException;
import com.telecombridge.gateway.dto.GrantedServiceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RequestCorrelator verifying timeout eviction, late arrival handling,
 * unmatched CCA handling, and memory leak prevention.
 */
class RequestCorrelatorTest {

    private RequestCorrelator correlator;

    @BeforeEach
    void setUp() {
        correlator = new RequestCorrelator();
    }

    @Test
    void register_and_complete_happyPath() throws Exception {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plusSeconds(5);

        correlator.register(1L, future, deadline, "session-1");

        assertThat(correlator.pendingCount()).isEqualTo(1);

        CcaData data = new CcaData("session-1", 2001L, 1, 0, null);
        correlator.complete(1L, data);

        assertThat(future.isDone()).isTrue();
        assertThat(future.get()).isEqualTo(data);
        assertThat(correlator.pendingCount()).isEqualTo(0);
    }

    @Test
    void evictExpired_removesTimedOutEntries_completesExceptionally() throws Exception {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        // Set deadline in the past so it's immediately expired
        Instant deadline = Instant.now().minusMillis(100);

        correlator.register(42L, future, deadline, "session-timeout");

        assertThat(correlator.pendingCount()).isEqualTo(1);

        // Trigger eviction
        correlator.evictExpired();

        assertThat(correlator.pendingCount()).isEqualTo(0);
        assertThat(future.isCompletedExceptionally()).isTrue();

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DiameterTimeoutException.class)
                .hasMessageContaining("timed out after")
                .hasMessageContaining("ms")
                .hasMessageContaining("session-timeout");
    }

    @Test
    void evictExpired_timeoutMessageIncludesElapsedDuration() throws Exception {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        // Set deadline in the past
        Instant deadline = Instant.now().minusMillis(50);

        correlator.register(99L, future, deadline, "sess-elapsed");

        correlator.evictExpired();

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DiameterTimeoutException.class)
                .cause()
                .hasMessageMatching("Request timed out after \\d+ms for session: sess-elapsed");
    }

    @Test
    void complete_afterTimeout_logsDebugForLateArrival() {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().minusMillis(100);

        correlator.register(10L, future, deadline, "session-late");

        // Evict the entry (simulates timeout)
        correlator.evictExpired();

        assertThat(correlator.pendingCount()).isEqualTo(0);
        assertThat(correlator.evictedCount()).isEqualTo(1);

        // Now a late CCA arrives for the same HbH ID
        CcaData lateData = new CcaData("session-late", 2001L, 1, 0, null);
        correlator.complete(10L, lateData);

        // The evicted ID should be removed from tracking after the late arrival
        assertThat(correlator.evictedCount()).isEqualTo(0);
    }

    @Test
    void complete_unknownHopByHopId_logsWarn() {
        // Complete with an ID that was never registered
        CcaData data = new CcaData("unknown-session", 2001L, 1, 0, null);
        correlator.complete(999L, data);

        // Should not throw, just log at WARN level
        assertThat(correlator.pendingCount()).isEqualTo(0);
    }

    @Test
    void completeExceptionally_removesEntry() {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plusSeconds(5);

        correlator.register(5L, future, deadline, "session-error");

        correlator.completeExceptionally(5L, new RuntimeException("test error"));

        assertThat(correlator.pendingCount()).isEqualTo(0);
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    void evictExpired_doesNotEvictNonExpiredEntries() {
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plusSeconds(60);

        correlator.register(7L, future, deadline, "session-active");

        correlator.evictExpired();

        assertThat(correlator.pendingCount()).isEqualTo(1);
        assertThat(future.isDone()).isFalse();
    }

    @Test
    void multipleRegistrations_evictsOnlyExpired() {
        CompletableFuture<CcaData> expiredFuture = new CompletableFuture<>();
        CompletableFuture<CcaData> activeFuture = new CompletableFuture<>();

        correlator.register(1L, expiredFuture, Instant.now().minusMillis(100), "expired-session");
        correlator.register(2L, activeFuture, Instant.now().plusSeconds(60), "active-session");

        correlator.evictExpired();

        assertThat(correlator.pendingCount()).isEqualTo(1);
        assertThat(expiredFuture.isCompletedExceptionally()).isTrue();
        assertThat(activeFuture.isDone()).isFalse();
    }
}
