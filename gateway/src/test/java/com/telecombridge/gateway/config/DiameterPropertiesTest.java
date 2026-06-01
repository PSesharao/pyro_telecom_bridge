package com.telecombridge.gateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for DiameterProperties range validation and defaults.
 */
class DiameterPropertiesTest {

    @Test
    void defaultValuesAreCorrect() {
        DiameterProperties props = new DiameterProperties();
        props.validate();

        assertEquals("localhost", props.getHost());
        assertEquals(3868, props.getPort());
        assertEquals("CTOPUP", props.getOriginHost());
        assertEquals("ctop.com", props.getOriginRealm());
        assertEquals("BSNL.NET", props.getDestinationRealm());
        assertEquals(5000, props.getRequestTimeoutMs());
        assertEquals(30000, props.getWatchdogIntervalMs());
        assertEquals(10000, props.getWatchdogTimeoutMs());
        assertEquals(4, props.getThreadPoolSize());
    }

    @Test
    void portOutOfRangeResetsToDefault() {
        DiameterProperties props = new DiameterProperties();
        props.setPort(0);
        props.validate();
        assertEquals(3868, props.getPort());

        props.setPort(65536);
        props.validate();
        assertEquals(3868, props.getPort());

        props.setPort(-1);
        props.validate();
        assertEquals(3868, props.getPort());
    }

    @Test
    void portInRangeIsAccepted() {
        DiameterProperties props = new DiameterProperties();
        props.setPort(1);
        props.validate();
        assertEquals(1, props.getPort());

        props.setPort(65535);
        props.validate();
        assertEquals(65535, props.getPort());

        props.setPort(8080);
        props.validate();
        assertEquals(8080, props.getPort());
    }

    @Test
    void requestTimeoutOutOfRangeResetsToDefault() {
        DiameterProperties props = new DiameterProperties();
        props.setRequestTimeoutMs(999);
        props.validate();
        assertEquals(5000, props.getRequestTimeoutMs());

        props.setRequestTimeoutMs(300001);
        props.validate();
        assertEquals(5000, props.getRequestTimeoutMs());
    }

    @Test
    void requestTimeoutInRangeIsAccepted() {
        DiameterProperties props = new DiameterProperties();
        props.setRequestTimeoutMs(1000);
        props.validate();
        assertEquals(1000, props.getRequestTimeoutMs());

        props.setRequestTimeoutMs(300000);
        props.validate();
        assertEquals(300000, props.getRequestTimeoutMs());
    }

    @Test
    void watchdogIntervalOutOfRangeResetsToDefault() {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogIntervalMs(5999);
        props.validate();
        assertEquals(30000, props.getWatchdogIntervalMs());

        props.setWatchdogIntervalMs(600001);
        props.validate();
        assertEquals(30000, props.getWatchdogIntervalMs());
    }

    @Test
    void watchdogIntervalInRangeIsAccepted() {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogIntervalMs(6000);
        props.validate();
        assertEquals(6000, props.getWatchdogIntervalMs());

        props.setWatchdogIntervalMs(600000);
        props.validate();
        assertEquals(600000, props.getWatchdogIntervalMs());
    }

    @Test
    void watchdogTimeoutOutOfRangeResetsToDefault() {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogTimeoutMs(999);
        props.validate();
        assertEquals(10000, props.getWatchdogTimeoutMs());

        props.setWatchdogTimeoutMs(300001);
        props.validate();
        assertEquals(10000, props.getWatchdogTimeoutMs());
    }

    @Test
    void watchdogTimeoutInRangeIsAccepted() {
        DiameterProperties props = new DiameterProperties();
        props.setWatchdogTimeoutMs(1000);
        props.validate();
        assertEquals(1000, props.getWatchdogTimeoutMs());

        props.setWatchdogTimeoutMs(300000);
        props.validate();
        assertEquals(300000, props.getWatchdogTimeoutMs());
    }

    @Test
    void threadPoolSizeOutOfRangeResetsToDefault() {
        DiameterProperties props = new DiameterProperties();
        props.setThreadPoolSize(0);
        props.validate();
        assertEquals(4, props.getThreadPoolSize());

        props.setThreadPoolSize(129);
        props.validate();
        assertEquals(4, props.getThreadPoolSize());

        props.setThreadPoolSize(-5);
        props.validate();
        assertEquals(4, props.getThreadPoolSize());
    }

    @Test
    void threadPoolSizeInRangeIsAccepted() {
        DiameterProperties props = new DiameterProperties();
        props.setThreadPoolSize(1);
        props.validate();
        assertEquals(1, props.getThreadPoolSize());

        props.setThreadPoolSize(128);
        props.validate();
        assertEquals(128, props.getThreadPoolSize());

        props.setThreadPoolSize(16);
        props.validate();
        assertEquals(16, props.getThreadPoolSize());
    }
}
