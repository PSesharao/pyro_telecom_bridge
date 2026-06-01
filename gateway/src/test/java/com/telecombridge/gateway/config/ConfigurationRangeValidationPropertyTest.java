package com.telecombridge.gateway.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Configuration Range Validation (Property 16).
 * <p>
 * **Validates: Requirements 11.2, 11.3, 11.5**
 * <p>
 * For any property value outside valid range, Gateway uses documented default and logs error.
 * For any property value inside valid range, Gateway preserves the configured value.
 */
@Tag("Feature: telecom-bridge, Property 16: Configuration Range Validation")
class ConfigurationRangeValidationPropertyTest {

    // Defaults from DiameterProperties
    private static final int DEFAULT_PORT = 3868;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    private static final int DEFAULT_WATCHDOG_INTERVAL_MS = 30000;
    private static final int DEFAULT_WATCHDOG_TIMEOUT_MS = 10000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;

    // --- Port: valid range 1-65535, default 3868 ---

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any port value within the valid range [1, 65535], the configured value is preserved.
     */
    @Property(tries = 100)
    void portInRangeIsPreserved(@ForAll @IntRange(min = 1, max = 65535) int port) {
        DiameterProperties props = new DiameterProperties();
        props.setPort(port);
        props.validate();

        assertEquals(port, props.getPort(),
                "Port value " + port + " is within valid range and should be preserved");
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any port value below the valid range, the default is used.
     */
    @Property(tries = 100)
    void portBelowRangeResetsToDefault(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int port) {
        DiameterProperties props = new DiameterProperties();
        props.setPort(port);
        props.validate();

        assertEquals(DEFAULT_PORT, props.getPort(),
                "Port value " + port + " is below valid range and should reset to default " + DEFAULT_PORT);
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any port value above the valid range, the default is used.
     */
    @Property(tries = 100)
    void portAboveRangeResetsToDefault(@ForAll @IntRange(min = 65536, max = Integer.MAX_VALUE) int port) {
        DiameterProperties props = new DiameterProperties();
        props.setPort(port);
        props.validate();

        assertEquals(DEFAULT_PORT, props.getPort(),
                "Port value " + port + " is above valid range and should reset to default " + DEFAULT_PORT);
    }

    // --- Request Timeout: valid range 1000-300000, default 5000 ---

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any requestTimeoutMs value within the valid range [1000, 300000], the configured value is preserved.
     */
    @Property(tries = 100)
    void requestTimeoutInRangeIsPreserved(@ForAll @IntRange(min = 1000, max = 300000) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setRequestTimeoutMs(timeout);
        props.validate();

        assertEquals(timeout, props.getRequestTimeoutMs(),
                "Request timeout " + timeout + " is within valid range and should be preserved");
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any requestTimeoutMs value below the valid range, the default is used.
     */
    @Property(tries = 100)
    void requestTimeoutBelowRangeResetsToDefault(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 999) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setRequestTimeoutMs(timeout);
        props.validate();

        assertEquals(DEFAULT_REQUEST_TIMEOUT_MS, props.getRequestTimeoutMs(),
                "Request timeout " + timeout + " is below valid range and should reset to default " + DEFAULT_REQUEST_TIMEOUT_MS);
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any requestTimeoutMs value above the valid range, the default is used.
     */
    @Property(tries = 100)
    void requestTimeoutAboveRangeResetsToDefault(@ForAll @IntRange(min = 300001, max = Integer.MAX_VALUE) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setRequestTimeoutMs(timeout);
        props.validate();

        assertEquals(DEFAULT_REQUEST_TIMEOUT_MS, props.getRequestTimeoutMs(),
                "Request timeout " + timeout + " is above valid range and should reset to default " + DEFAULT_REQUEST_TIMEOUT_MS);
    }

    // --- Watchdog Interval: valid range 6000-600000, default 30000 ---

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogIntervalMs value within the valid range [6000, 600000], the configured value is preserved.
     */
    @Property(tries = 100)
    void watchdogIntervalInRangeIsPreserved(@ForAll @IntRange(min = 6000, max = 600000) int interval) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogIntervalMs(interval);
        props.validate();

        assertEquals(interval, props.getWatchdogIntervalMs(),
                "Watchdog interval " + interval + " is within valid range and should be preserved");
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogIntervalMs value below the valid range, the default is used.
     */
    @Property(tries = 100)
    void watchdogIntervalBelowRangeResetsToDefault(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 5999) int interval) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogIntervalMs(interval);
        props.validate();

        assertEquals(DEFAULT_WATCHDOG_INTERVAL_MS, props.getWatchdogIntervalMs(),
                "Watchdog interval " + interval + " is below valid range and should reset to default " + DEFAULT_WATCHDOG_INTERVAL_MS);
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogIntervalMs value above the valid range, the default is used.
     */
    @Property(tries = 100)
    void watchdogIntervalAboveRangeResetsToDefault(@ForAll @IntRange(min = 600001, max = Integer.MAX_VALUE) int interval) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogIntervalMs(interval);
        props.validate();

        assertEquals(DEFAULT_WATCHDOG_INTERVAL_MS, props.getWatchdogIntervalMs(),
                "Watchdog interval " + interval + " is above valid range and should reset to default " + DEFAULT_WATCHDOG_INTERVAL_MS);
    }

    // --- Watchdog Timeout: valid range 1000-300000, default 10000 ---

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogTimeoutMs value within the valid range [1000, 300000], the configured value is preserved.
     */
    @Property(tries = 100)
    void watchdogTimeoutInRangeIsPreserved(@ForAll @IntRange(min = 1000, max = 300000) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogTimeoutMs(timeout);
        props.validate();

        assertEquals(timeout, props.getWatchdogTimeoutMs(),
                "Watchdog timeout " + timeout + " is within valid range and should be preserved");
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogTimeoutMs value below the valid range, the default is used.
     */
    @Property(tries = 100)
    void watchdogTimeoutBelowRangeResetsToDefault(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 999) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogTimeoutMs(timeout);
        props.validate();

        assertEquals(DEFAULT_WATCHDOG_TIMEOUT_MS, props.getWatchdogTimeoutMs(),
                "Watchdog timeout " + timeout + " is below valid range and should reset to default " + DEFAULT_WATCHDOG_TIMEOUT_MS);
    }

    /**
     * **Validates: Requirements 11.2, 11.5**
     * <p>
     * For any watchdogTimeoutMs value above the valid range, the default is used.
     */
    @Property(tries = 100)
    void watchdogTimeoutAboveRangeResetsToDefault(@ForAll @IntRange(min = 300001, max = Integer.MAX_VALUE) int timeout) {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogTimeoutMs(timeout);
        props.validate();

        assertEquals(DEFAULT_WATCHDOG_TIMEOUT_MS, props.getWatchdogTimeoutMs(),
                "Watchdog timeout " + timeout + " is above valid range and should reset to default " + DEFAULT_WATCHDOG_TIMEOUT_MS);
    }

    // --- Thread Pool Size: valid range 1-128, default 4 ---

    /**
     * **Validates: Requirements 11.3, 11.5**
     * <p>
     * For any threadPoolSize value within the valid range [1, 128], the configured value is preserved.
     */
    @Property(tries = 100)
    void threadPoolSizeInRangeIsPreserved(@ForAll @IntRange(min = 1, max = 128) int poolSize) {
        DiameterProperties props = new DiameterProperties();
        props.setThreadPoolSize(poolSize);
        props.validate();

        assertEquals(poolSize, props.getThreadPoolSize(),
                "Thread pool size " + poolSize + " is within valid range and should be preserved");
    }

    /**
     * **Validates: Requirements 11.3, 11.5**
     * <p>
     * For any threadPoolSize value below the valid range, the default is used.
     */
    @Property(tries = 100)
    void threadPoolSizeBelowRangeResetsToDefault(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int poolSize) {
        DiameterProperties props = new DiameterProperties();
        props.setThreadPoolSize(poolSize);
        props.validate();

        assertEquals(DEFAULT_THREAD_POOL_SIZE, props.getThreadPoolSize(),
                "Thread pool size " + poolSize + " is below valid range and should reset to default " + DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * **Validates: Requirements 11.3, 11.5**
     * <p>
     * For any threadPoolSize value above the valid range, the default is used.
     */
    @Property(tries = 100)
    void threadPoolSizeAboveRangeResetsToDefault(@ForAll @IntRange(min = 129, max = Integer.MAX_VALUE) int poolSize) {
        DiameterProperties props = new DiameterProperties();
        props.setThreadPoolSize(poolSize);
        props.validate();

        assertEquals(DEFAULT_THREAD_POOL_SIZE, props.getThreadPoolSize(),
                "Thread pool size " + poolSize + " is above valid range and should reset to default " + DEFAULT_THREAD_POOL_SIZE);
    }
}
