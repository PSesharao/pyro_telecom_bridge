package com.telecombridge.gateway.diameter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique Diameter Session-IDs following the format:
 * "{originHost};{nanoTime};{sequence}"
 *
 * Example: "CTOPUP;1111;5183843290"
 */
@Component
public class SessionIdGenerator {

    private final AtomicLong sequence = new AtomicLong(0);
    private final String originHost;

    /**
     * Creates a SessionIdGenerator with the specified origin host.
     *
     * @param originHost the Origin-Host value used as the Session-ID prefix
     */
    public SessionIdGenerator(@Value("${diameter.origin-host:CTOPUP}") String originHost) {
        this.originHost = originHost;
    }

    /**
     * Generates a unique Session-ID in the format "{originHost};{nanoTime};{sequence}".
     * Each call produces a distinct value due to the combination of nanosecond timestamp
     * and monotonically increasing sequence number.
     *
     * @return a unique Session-ID string
     */
    public String generate() {
        return originHost + ";" + System.nanoTime() + ";" + sequence.incrementAndGet();
    }
}
