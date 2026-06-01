package com.telecombridge.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Diameter client connection.
 * Validates ranges at startup and falls back to defaults for out-of-range values.
 */
@ConfigurationProperties(prefix = "diameter")
public class DiameterProperties {

    private static final Logger log = LoggerFactory.getLogger(DiameterProperties.class);

    // Defaults
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3868;
    private static final String DEFAULT_ORIGIN_HOST = "CTOPUP";
    private static final String DEFAULT_ORIGIN_REALM = "ctop.com";
    private static final String DEFAULT_DESTINATION_REALM = "BSNL.NET";
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    private static final int DEFAULT_WATCHDOG_INTERVAL_MS = 30000;
    private static final int DEFAULT_WATCHDOG_TIMEOUT_MS = 10000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String originHost = DEFAULT_ORIGIN_HOST;
    private String originRealm = DEFAULT_ORIGIN_REALM;
    private String destinationRealm = DEFAULT_DESTINATION_REALM;
    private int requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
    private int watchdogIntervalMs = DEFAULT_WATCHDOG_INTERVAL_MS;
    private int watchdogTimeoutMs = DEFAULT_WATCHDOG_TIMEOUT_MS;
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    @PostConstruct
    public void validate() {
        if (port < 1 || port > 65535) {
            log.error("Invalid diameter.port value: {}. Accepted range: 1-65535. Using default: {}",
                    port, DEFAULT_PORT);
            this.port = DEFAULT_PORT;
        }

        if (requestTimeoutMs < 1000 || requestTimeoutMs > 300000) {
            log.error("Invalid diameter.request-timeout-ms value: {}. Accepted range: 1000-300000. Using default: {}",
                    requestTimeoutMs, DEFAULT_REQUEST_TIMEOUT_MS);
            this.requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
        }

        if (watchdogIntervalMs < 6000 || watchdogIntervalMs > 600000) {
            log.error("Invalid diameter.watchdog-interval-ms value: {}. Accepted range: 6000-600000. Using default: {}",
                    watchdogIntervalMs, DEFAULT_WATCHDOG_INTERVAL_MS);
            this.watchdogIntervalMs = DEFAULT_WATCHDOG_INTERVAL_MS;
        }

        if (watchdogTimeoutMs < 1000 || watchdogTimeoutMs > 300000) {
            log.error("Invalid diameter.watchdog-timeout-ms value: {}. Accepted range: 1000-300000. Using default: {}",
                    watchdogTimeoutMs, DEFAULT_WATCHDOG_TIMEOUT_MS);
            this.watchdogTimeoutMs = DEFAULT_WATCHDOG_TIMEOUT_MS;
        }

        if (threadPoolSize < 1 || threadPoolSize > 128) {
            log.error("Invalid diameter.thread-pool-size value: {}. Accepted range: 1-128. Using default: {}",
                    threadPoolSize, DEFAULT_THREAD_POOL_SIZE);
            this.threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getOriginHost() {
        return originHost;
    }

    public void setOriginHost(String originHost) {
        this.originHost = originHost;
    }

    public String getOriginRealm() {
        return originRealm;
    }

    public void setOriginRealm(String originRealm) {
        this.originRealm = originRealm;
    }

    public String getDestinationRealm() {
        return destinationRealm;
    }

    public void setDestinationRealm(String destinationRealm) {
        this.destinationRealm = destinationRealm;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getWatchdogIntervalMs() {
        return watchdogIntervalMs;
    }

    public void setWatchdogIntervalMs(int watchdogIntervalMs) {
        this.watchdogIntervalMs = watchdogIntervalMs;
    }

    public int getWatchdogTimeoutMs() {
        return watchdogTimeoutMs;
    }

    public void setWatchdogTimeoutMs(int watchdogTimeoutMs) {
        this.watchdogTimeoutMs = watchdogTimeoutMs;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }
}
