package com.telecombridge.gateway.diameter;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a pending Diameter request awaiting a CCA response.
 *
 * @param future         the CompletableFuture to be completed when the CCA arrives or timeout occurs
 * @param deadline       the instant after which this request is considered timed out
 * @param sessionId      the Session-Id associated with this request (for logging)
 * @param registeredAt   the instant when this request was registered (for elapsed time calculation)
 */
public record PendingRequest(
        CompletableFuture<CcaData> future,
        Instant deadline,
        String sessionId,
        Instant registeredAt
) {
    /**
     * Convenience constructor that sets registeredAt to the current time.
     */
    public PendingRequest(CompletableFuture<CcaData> future, Instant deadline, String sessionId) {
        this(future, deadline, sessionId, Instant.now());
    }
}
