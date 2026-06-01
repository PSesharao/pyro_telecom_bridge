package com.telecombridge.gateway.diameter;

import com.telecombridge.codec.DiameterTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe correlation map that matches Diameter CCA responses to pending CCR requests
 * using the Hop-by-Hop ID. Handles timeout eviction via a scheduled task.
 */
@Component
public class RequestCorrelator {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelator.class);

    private final ConcurrentHashMap<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Tracks Hop-by-Hop IDs that were evicted due to timeout.
     * Used to distinguish late CCA arrivals (DEBUG) from truly unmatched CCAs (WARN).
     */
    private final Set<Long> evictedIds = ConcurrentHashMap.newKeySet();

    /**
     * Registers a pending request in the correlation map.
     *
     * @param hopByHopId the Hop-by-Hop ID used to correlate the response
     * @param future     the CompletableFuture to complete when the CCA arrives
     * @param deadline   the instant after which the request is considered timed out
     * @param sessionId  the Session-Id for logging purposes
     * @return the same CompletableFuture for chaining
     */
    public CompletableFuture<CcaData> register(long hopByHopId, CompletableFuture<CcaData> future,
                                                Instant deadline, String sessionId) {
        pendingRequests.put(hopByHopId, new PendingRequest(future, deadline, sessionId));
        return future;
    }

    /**
     * Completes a pending request successfully with the given CCA data.
     * Removes the entry from the map. If the entry is not found:
     * - Logs at DEBUG level if the HbH ID was previously evicted (late arrival after timeout)
     * - Logs at WARN level if the HbH ID is truly unknown (never registered or already completed)
     *
     * @param hopByHopId the Hop-by-Hop ID of the response
     * @param data       the parsed CCA data
     */
    public void complete(long hopByHopId, CcaData data) {
        PendingRequest pending = pendingRequests.remove(hopByHopId);
        if (pending != null) {
            pending.future().complete(data);
        } else if (evictedIds.remove(hopByHopId)) {
            log.debug("event=cca_late_arrival hopByHopId={}", hopByHopId);
        } else {
            log.warn("event=cca_unmatched hopByHopId={}", hopByHopId);
        }
    }

    /**
     * Completes a pending request exceptionally with the given error.
     * Removes the entry from the map.
     *
     * @param hopByHopId the Hop-by-Hop ID of the failed request
     * @param error      the error to complete the future with
     */
    public void completeExceptionally(long hopByHopId, Throwable error) {
        PendingRequest pending = pendingRequests.remove(hopByHopId);
        if (pending != null) {
            pending.future().completeExceptionally(error);
        }
    }

    /**
     * Scheduled task that scans the correlation map every 1 second and evicts
     * entries that have exceeded their deadline. Timed-out entries are completed
     * exceptionally with a {@link DiameterTimeoutException} that includes the elapsed duration.
     */
    @Scheduled(fixedRate = 1000)
    public void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<Long, PendingRequest>> iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, PendingRequest> entry = iterator.next();
            PendingRequest pending = entry.getValue();
            if (now.isAfter(pending.deadline())) {
                iterator.remove();
                long elapsedMs = Duration.between(pending.registeredAt(), now).toMillis();
                // Track evicted ID for late arrival detection
                evictedIds.add(entry.getKey());
                pending.future().completeExceptionally(
                        new DiameterTimeoutException(
                                "Request timed out after " + elapsedMs + "ms for session: " + pending.sessionId()));
                log.debug("event=request_timeout hopByHopId={} sessionId={} elapsedMs={}",
                        entry.getKey(), pending.sessionId(), elapsedMs);
            }
        }
    }

    /**
     * Returns the number of pending requests currently in the map.
     * Useful for metrics and testing.
     *
     * @return the count of pending requests
     */
    public int pendingCount() {
        return pendingRequests.size();
    }

    /**
     * Returns the number of evicted IDs being tracked (for testing).
     */
    int evictedCount() {
        return evictedIds.size();
    }
}
