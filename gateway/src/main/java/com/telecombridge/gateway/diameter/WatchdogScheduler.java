package com.telecombridge.gateway.diameter;

import com.telecombridge.codec.Avp;
import com.telecombridge.codec.AvpCodes;
import com.telecombridge.codec.CommandCodes;
import com.telecombridge.codec.DiameterHeader;
import com.telecombridge.codec.DiameterMessage;
import com.telecombridge.gateway.config.DiameterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages Device Watchdog (DWR/DWA) exchanges to maintain connection liveness.
 * <p>
 * Sends DWR messages at a configurable interval and monitors for DWA responses.
 * If no DWA is received within the configured timeout, the connection is closed
 * and reconnection is triggered.
 * <p>
 * Also responds to incoming DWR messages from the server with DWA answers.
 */
@Component
public class WatchdogScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatchdogScheduler.class);

    private final DiameterClient diameterClient;
    private final DiameterProperties properties;
    private final IdGenerator idGenerator;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "watchdog-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** Origin-State-Id: seconds since epoch at startup (per RFC 6733). */
    private final long originStateId = System.currentTimeMillis() / 1000;

    private volatile boolean awaitingDwa = false;
    private volatile long lastDwaReceivedTime;
    private volatile boolean running = false;

    private ScheduledFuture<?> nextDwrTask;
    private ScheduledFuture<?> dwaTimeoutTask;

    public WatchdogScheduler(@Lazy DiameterClient diameterClient,
                             DiameterProperties properties,
                             IdGenerator idGenerator) {
        this.diameterClient = diameterClient;
        this.properties = properties;
        this.idGenerator = idGenerator;
    }

    /**
     * Starts the watchdog scheduler. Called when the connection becomes READY
     * (after successful CEA exchange).
     * <p>
     * Schedules the first DWR after the full watchdog interval has elapsed.
     */
    public synchronized void start() {
        if (running) {
            log.debug("Watchdog scheduler already running, ignoring start request");
            return;
        }

        running = true;
        awaitingDwa = false;
        lastDwaReceivedTime = System.currentTimeMillis();

        log.info("Watchdog scheduler started, first DWR in {}ms", properties.getWatchdogIntervalMs());

        scheduleNextDwr(properties.getWatchdogIntervalMs());
    }

    /**
     * Stops the watchdog scheduler. Cancels any pending DWR or timeout tasks.
     */
    public synchronized void stop() {
        running = false;
        awaitingDwa = false;

        if (nextDwrTask != null) {
            nextDwrTask.cancel(false);
            nextDwrTask = null;
        }
        if (dwaTimeoutTask != null) {
            dwaTimeoutTask.cancel(false);
            dwaTimeoutTask = null;
        }

        log.info("Watchdog scheduler stopped");
    }

    /**
     * Called when a DWA is received from the server.
     * Resets the awaiting flag and schedules the next DWR after the full interval.
     */
    public synchronized void onDwaReceived() {
        if (!running) {
            return;
        }

        awaitingDwa = false;
        lastDwaReceivedTime = System.currentTimeMillis();

        // Cancel the timeout task since we received the DWA
        if (dwaTimeoutTask != null) {
            dwaTimeoutTask.cancel(false);
            dwaTimeoutTask = null;
        }

        log.debug("DWA received, scheduling next DWR in {}ms", properties.getWatchdogIntervalMs());
        scheduleNextDwr(properties.getWatchdogIntervalMs());
    }

    /**
     * Handles an incoming DWR from the server by responding with a DWA.
     * The DWA contains Result_Code 2001, Origin-Host, and Origin-Realm,
     * and preserves the Hop-by-Hop and End-to-End IDs from the request.
     *
     * @param incomingDwr the incoming DWR message
     */
    public void handleIncomingDwr(DiameterMessage incomingDwr) {
        log.debug("Received incoming DWR: hopByHopId={}, endToEndId={}",
                incomingDwr.getHeader().hopByHopId(), incomingDwr.getHeader().endToEndId());

        List<Avp> avps = new ArrayList<>();

        // Result-Code (268) - Unsigned32, value 2001 (DIAMETER_SUCCESS)
        avps.add(new Avp(AvpCodes.RESULT_CODE, Avp.FLAG_MANDATORY, encodeUnsigned32(2001)));

        // Origin-Host (264) - UTF8String, Mandatory
        avps.add(new Avp(AvpCodes.ORIGIN_HOST, Avp.FLAG_MANDATORY,
                properties.getOriginHost().getBytes(StandardCharsets.UTF_8)));

        // Origin-Realm (296) - UTF8String, Mandatory
        avps.add(new Avp(AvpCodes.ORIGIN_REALM, Avp.FLAG_MANDATORY,
                properties.getOriginRealm().getBytes(StandardCharsets.UTF_8)));

        // Build DWA header: Command Code 280, App-ID 0, Answer (no Request flag)
        // Preserve HbH and E2E IDs from the request
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // message length calculated by encoder
                (byte) 0, // Answer: no Request flag
                CommandCodes.DEVICE_WATCHDOG,
                0, // Application-ID 0 for DWA
                incomingDwr.getHeader().hopByHopId(),
                incomingDwr.getHeader().endToEndId()
        );

        DiameterMessage dwa = new DiameterMessage(header, avps);

        diameterClient.send(dwa).whenComplete((v, ex) -> {
            if (ex != null) {
                log.error("Failed to send DWA: {}", ex.getMessage());
            } else {
                log.debug("DWA sent: hopByHopId={}, endToEndId={}",
                        header.hopByHopId(), header.endToEndId());
            }
        });
    }

    /**
     * Sends a DWR message to the Diameter server.
     * Includes Origin-Host, Origin-Realm, and Origin-State-Id AVPs.
     */
    private void sendDwr() {
        if (!running) {
            return;
        }

        List<Avp> avps = new ArrayList<>();

        // Origin-Host (264) - UTF8String, Mandatory
        avps.add(new Avp(AvpCodes.ORIGIN_HOST, Avp.FLAG_MANDATORY,
                properties.getOriginHost().getBytes(StandardCharsets.UTF_8)));

        // Origin-Realm (296) - UTF8String, Mandatory
        avps.add(new Avp(AvpCodes.ORIGIN_REALM, Avp.FLAG_MANDATORY,
                properties.getOriginRealm().getBytes(StandardCharsets.UTF_8)));

        // Origin-State-Id (278) - Unsigned32, Mandatory
        avps.add(new Avp(AvpCodes.ORIGIN_STATE_ID, Avp.FLAG_MANDATORY, encodeUnsigned32(originStateId)));

        // Build DWR header: Command Code 280, App-ID 0, Request flag (0x80)
        long hopByHopId = idGenerator.nextHopByHopId();
        long endToEndId = idGenerator.nextEndToEndId();
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // message length calculated by encoder
                DiameterHeader.FLAG_REQUEST,
                CommandCodes.DEVICE_WATCHDOG,
                0, // Application-ID 0 for DWR
                hopByHopId,
                endToEndId
        );

        DiameterMessage dwr = new DiameterMessage(header, avps);

        awaitingDwa = true;

        diameterClient.send(dwr).whenComplete((v, ex) -> {
            if (ex != null) {
                log.error("Failed to send DWR: {}", ex.getMessage());
                awaitingDwa = false;
                // Connection is likely broken; the channel inactive handler will trigger reconnect
            } else {
                log.debug("DWR sent: hopByHopId={}, endToEndId={}", hopByHopId, endToEndId);
                // Schedule timeout check
                scheduleDwaTimeout();
            }
        });
    }

    /**
     * Schedules the next DWR to be sent after the specified delay.
     */
    private void scheduleNextDwr(long delayMs) {
        if (nextDwrTask != null) {
            nextDwrTask.cancel(false);
        }
        nextDwrTask = scheduler.schedule(this::sendDwr, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules a timeout check for the DWA response.
     * If no DWA is received within the configured timeout, the connection is closed
     * and reconnection is triggered.
     */
    private void scheduleDwaTimeout() {
        if (dwaTimeoutTask != null) {
            dwaTimeoutTask.cancel(false);
        }
        dwaTimeoutTask = scheduler.schedule(this::checkDwaTimeout,
                properties.getWatchdogTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Checks if a DWA was received within the timeout period.
     * If still awaiting DWA, closes the connection and triggers reconnection.
     */
    private void checkDwaTimeout() {
        if (!running) {
            return;
        }

        if (awaitingDwa) {
            log.warn("DWA timeout: no response received within {}ms, closing connection and reconnecting",
                    properties.getWatchdogTimeoutMs());
            awaitingDwa = false;
            stop();
            diameterClient.closeAndReconnect();
        }
    }

    /**
     * Returns whether the scheduler is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns whether the scheduler is currently awaiting a DWA response.
     */
    public boolean isAwaitingDwa() {
        return awaitingDwa;
    }

    /**
     * Encodes an unsigned 32-bit value as 4 bytes big-endian.
     */
    private byte[] encodeUnsigned32(long value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return data;
    }
}
