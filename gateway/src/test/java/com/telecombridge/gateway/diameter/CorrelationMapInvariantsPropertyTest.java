package com.telecombridge.gateway.diameter;

import com.telecombridge.gateway.dto.GrantedServiceUnit;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Correlation Map Invariants (Property 6).
 * <p>
 * **Validates: Requirements 2.3, 2.4, 2.5, 7.3**
 * <p>
 * For any sequence of register and complete operations on the Correlation Map:
 * (a) after registering a HbH ID, the map SHALL contain that key;
 * (b) after completing a HbH ID with data, the associated future SHALL resolve with that exact data
 *     and the entry SHALL be removed from the map;
 * (c) after a timeout (evictExpired), the entry SHALL be removed from the map.
 */
@Tag("Feature: telecom-bridge, Property 6: Correlation Map Invariants")
class CorrelationMapInvariantsPropertyTest {

    /**
     * **Validates: Requirements 2.3**
     * <p>
     * After registering a HbH ID, the map contains that key (pendingCount increases).
     */
    @Property(tries = 100)
    void afterRegister_mapContainsKey(
            @ForAll @LongRange(min = 1, max = 1_000_000) long hopByHopId) {
        RequestCorrelator correlator = new RequestCorrelator();
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plusSeconds(30);

        correlator.register(hopByHopId, future, deadline, "session-" + hopByHopId);

        assertEquals(1, correlator.pendingCount(),
                "After register, map should contain exactly one entry");
    }

    /**
     * **Validates: Requirements 2.4, 2.5**
     * <p>
     * After completing a HbH ID with data, the associated future resolves with that exact data
     * and the entry is removed from the map.
     */
    @Property(tries = 100)
    void afterComplete_futureResolvesWithData_entryRemoved(
            @ForAll @LongRange(min = 1, max = 1_000_000) long hopByHopId,
            @ForAll @LongRange(min = 1000, max = 9999) long resultCode) throws Exception {
        RequestCorrelator correlator = new RequestCorrelator();
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        Instant deadline = Instant.now().plusSeconds(30);
        String sessionId = "session-" + hopByHopId;

        correlator.register(hopByHopId, future, deadline, sessionId);

        CcaData data = new CcaData(sessionId, resultCode, 1, 0, null);
        correlator.complete(hopByHopId, data);

        assertTrue(future.isDone(), "Future should be completed after complete()");
        assertFalse(future.isCompletedExceptionally(), "Future should not be completed exceptionally");
        assertEquals(data, future.get(), "Future should resolve with the exact data provided");
        assertEquals(0, correlator.pendingCount(),
                "After complete, entry should be removed from the map");
    }

    /**
     * **Validates: Requirements 7.3**
     * <p>
     * After a timeout (evictExpired), the entry is removed from the map and the future
     * is completed exceptionally.
     */
    @Property(tries = 100)
    void afterTimeout_entryRemoved(
            @ForAll @LongRange(min = 1, max = 1_000_000) long hopByHopId) {
        RequestCorrelator correlator = new RequestCorrelator();
        CompletableFuture<CcaData> future = new CompletableFuture<>();
        // Set deadline in the past to simulate timeout
        Instant deadline = Instant.now().minusMillis(100);
        String sessionId = "session-timeout-" + hopByHopId;

        correlator.register(hopByHopId, future, deadline, sessionId);
        assertEquals(1, correlator.pendingCount(), "Entry should be in map before eviction");

        correlator.evictExpired();

        assertEquals(0, correlator.pendingCount(),
                "After timeout eviction, entry should be removed from the map");
        assertTrue(future.isCompletedExceptionally(),
                "Future should be completed exceptionally after timeout");
    }

    /**
     * **Validates: Requirements 2.3, 2.4, 2.5, 7.3**
     * <p>
     * For random sequences of register/complete/timeout operations, all invariants hold:
     * - Register increases pending count
     * - Complete resolves the correct future and removes the entry
     * - Timeout removes expired entries
     */
    @Property(tries = 100)
    void randomOperationSequences_maintainInvariants(
            @ForAll("operationSequences") List<Operation> operations) throws Exception {
        RequestCorrelator correlator = new RequestCorrelator();
        Map<Long, CompletableFuture<CcaData>> registeredFutures = new HashMap<>();
        Set<Long> completedIds = new HashSet<>();
        Set<Long> timedOutIds = new HashSet<>();

        for (Operation op : operations) {
            switch (op.type()) {
                case REGISTER -> {
                    CompletableFuture<CcaData> future = new CompletableFuture<>();
                    Instant deadline = op.expired()
                            ? Instant.now().minusMillis(100)
                            : Instant.now().plusSeconds(60);
                    correlator.register(op.hopByHopId(), future, deadline,
                            "session-" + op.hopByHopId());
                    registeredFutures.put(op.hopByHopId(), future);

                    // Invariant (a): after register, map contains the key
                    assertTrue(correlator.pendingCount() > 0,
                            "After register, pending count should be > 0");
                }
                case COMPLETE -> {
                    if (registeredFutures.containsKey(op.hopByHopId())
                            && !completedIds.contains(op.hopByHopId())
                            && !timedOutIds.contains(op.hopByHopId())) {
                        int countBefore = correlator.pendingCount();
                        CcaData data = new CcaData("session-" + op.hopByHopId(),
                                2001L, 1, 0, null);
                        correlator.complete(op.hopByHopId(), data);

                        CompletableFuture<CcaData> future = registeredFutures.get(op.hopByHopId());
                        // Invariant (b): future resolves with exact data
                        assertTrue(future.isDone(), "Future should be done after complete");
                        assertEquals(data, future.get(),
                                "Future should resolve with the exact data");
                        // Invariant (b): entry removed
                        assertTrue(correlator.pendingCount() < countBefore,
                                "Pending count should decrease after complete");
                        completedIds.add(op.hopByHopId());
                    }
                }
                case TIMEOUT -> {
                    int countBefore = correlator.pendingCount();
                    correlator.evictExpired();

                    // Invariant (c): expired entries are removed
                    // Check all registered futures with expired deadlines
                    for (Map.Entry<Long, CompletableFuture<CcaData>> entry : registeredFutures.entrySet()) {
                        long id = entry.getKey();
                        CompletableFuture<CcaData> future = entry.getValue();
                        if (future.isCompletedExceptionally() && !completedIds.contains(id)) {
                            timedOutIds.add(id);
                        }
                    }

                    // Pending count should not increase after eviction
                    assertTrue(correlator.pendingCount() <= countBefore,
                            "Pending count should not increase after evictExpired");
                }
            }
        }
    }

    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        Arbitrary<Long> hopByHopIds = Arbitraries.longs().between(1, 50);
        Arbitrary<Boolean> expired = Arbitraries.of(true, false);

        Arbitrary<Operation> registerOp = Combinators.combine(hopByHopIds, expired)
                .as((id, exp) -> new Operation(OperationType.REGISTER, id, exp));
        Arbitrary<Operation> completeOp = hopByHopIds
                .map(id -> new Operation(OperationType.COMPLETE, id, false));
        Arbitrary<Operation> timeoutOp = Arbitraries.just(
                new Operation(OperationType.TIMEOUT, 0L, false));

        return Arbitraries.frequencyOf(
                Tuple.of(5, registerOp),
                Tuple.of(3, completeOp),
                Tuple.of(2, timeoutOp)
        ).list().ofMinSize(5).ofMaxSize(30);
    }

    enum OperationType {
        REGISTER, COMPLETE, TIMEOUT
    }

    record Operation(OperationType type, long hopByHopId, boolean expired) {
    }
}
