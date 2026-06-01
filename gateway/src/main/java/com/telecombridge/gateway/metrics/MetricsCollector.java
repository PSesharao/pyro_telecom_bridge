package com.telecombridge.gateway.metrics;

import com.telecombridge.gateway.diameter.RequestCorrelator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects request processing metrics and reports them at a configurable interval.
 * Tracks latencies, total requests processed, and pending request count.
 */
@Component
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final RequestCorrelator correlator;

    public MetricsCollector(RequestCorrelator correlator) {
        this.correlator = correlator;
    }

    /**
     * Records a request latency in milliseconds.
     *
     * @param latencyMs the latency of the request in milliseconds
     */
    public void recordLatency(long latencyMs) {
        latencies.add(latencyMs);
        totalProcessed.incrementAndGet();
    }

    /**
     * Scheduled task that reports metrics at a configurable interval.
     * Logs average latency, p95 latency, pending count, and total processed.
     */
    @Scheduled(fixedDelayString = "${metrics.interval-ms:60000}")
    public void reportMetrics() {
        List<Long> snapshot = new ArrayList<>();
        Long value;
        while ((value = latencies.poll()) != null) {
            snapshot.add(value);
        }

        int count = snapshot.size();
        int pendingCount = correlator.pendingCount();
        long total = totalProcessed.get();

        if (count == 0) {
            log.info("event=metrics_report avgLatencyMs=0 p95LatencyMs=0 pendingCount={} totalProcessed={} intervalRequests=0",
                    pendingCount, total);
            return;
        }

        Collections.sort(snapshot);

        long sum = 0;
        for (long l : snapshot) {
            sum += l;
        }
        long avgLatency = sum / count;

        int p95Index = (int) Math.ceil(count * 0.95) - 1;
        if (p95Index < 0) p95Index = 0;
        if (p95Index >= count) p95Index = count - 1;
        long p95Latency = snapshot.get(p95Index);

        log.info("event=metrics_report avgLatencyMs={} p95LatencyMs={} pendingCount={} totalProcessed={} intervalRequests={}",
                avgLatency, p95Latency, pendingCount, total, count);
    }
}
