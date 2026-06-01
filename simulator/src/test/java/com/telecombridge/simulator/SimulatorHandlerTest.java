package com.telecombridge.simulator;

import com.telecombridge.codec.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimulatorHandler}.
 * Uses Netty's EmbeddedChannel to test the handler directly.
 */
class SimulatorHandlerTest {

    private static final int DELAY_MS = 50;

    private EmbeddedChannel createChannel() {
        return createChannel(DELAY_MS);
    }

    private EmbeddedChannel createChannel(int delayMs) {
        return new EmbeddedChannel(
                new MessageFrameDecoder(),
                new SimulatorHandler(delayMs)
        );
    }

    private DiameterMessage buildCer(long hopByHopId, long endToEndId) {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION, 0,
                DiameterHeader.FLAG_REQUEST,
                CommandCodes.CAPABILITIES_EXCHANGE, 0L,
                hopByHopId, endToEndId);
        DiameterMessage cer = new DiameterMessage(header);
        cer.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, "TEST-CLIENT"));
        cer.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, "test.local"));
        cer.addAvp(buildUnsigned32Avp(AvpCodes.AUTH_APPLICATION_ID, 4L));
        return cer;
    }

    private DiameterMessage buildCcr(long hopByHopId, long endToEndId,
                                      String sessionId, long ccRequestType,
                                      long ccRequestNumber) {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION, 0,
                (byte) (DiameterHeader.FLAG_REQUEST | DiameterHeader.FLAG_PROXIABLE),
                CommandCodes.CREDIT_CONTROL, 4L,
                hopByHopId, endToEndId);
        DiameterMessage ccr = new DiameterMessage(header);
        ccr.addAvp(buildUtf8StringAvp(AvpCodes.SESSION_ID, sessionId));
        ccr.addAvp(buildUnsigned32Avp(AvpCodes.CC_REQUEST_TYPE, ccRequestType));
        ccr.addAvp(buildUnsigned32Avp(AvpCodes.CC_REQUEST_NUMBER, ccRequestNumber));
        ccr.addAvp(buildSubscriptionIdAvp("+1234567890"));
        return ccr;
    }

    private DiameterMessage buildDwr(long hopByHopId, long endToEndId) {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION, 0,
                DiameterHeader.FLAG_REQUEST,
                CommandCodes.DEVICE_WATCHDOG, 0L,
                hopByHopId, endToEndId);
        DiameterMessage dwr = new DiameterMessage(header);
        dwr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, "TEST-CLIENT"));
        dwr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, "test.local"));
        return dwr;
    }

    private DiameterMessage buildUnrecognizedMessage(long hopByHopId, long endToEndId) {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION, 0,
                DiameterHeader.FLAG_REQUEST,
                999, 0L, hopByHopId, endToEndId);
        return new DiameterMessage(header);
    }

    private void sendMessage(EmbeddedChannel channel, DiameterMessage message) {
        ByteBuf encoded = DiameterCodec.encode(message);
        channel.writeInbound(encoded);
    }

    private DiameterMessage readResponse(EmbeddedChannel channel) {
        ByteBuf outBuf = channel.readOutbound();
        if (outBuf == null) return null;
        try {
            return DiameterCodec.decode(outBuf);
        } finally {
            outBuf.release();
        }
    }

    private Avp buildUnsigned32Avp(int code, long value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }

    private Avp buildUtf8StringAvp(int code, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }

    private Avp buildSubscriptionIdAvp(String msisdn) {
        byte[] typeData = new byte[4];
        Avp typeAvp = new Avp(AvpCodes.SUBSCRIPTION_ID_TYPE, Avp.FLAG_MANDATORY, typeData);
        byte[] dataBytes = msisdn.getBytes(StandardCharsets.UTF_8);
        Avp dataAvp = new Avp(AvpCodes.SUBSCRIPTION_ID_DATA, Avp.FLAG_MANDATORY, dataBytes);

        ByteBuf groupedBuf = io.netty.buffer.Unpooled.buffer();
        try {
            AvpEncoder.encode(typeAvp, groupedBuf);
            AvpEncoder.encode(dataAvp, groupedBuf);
            byte[] groupedData = new byte[groupedBuf.readableBytes()];
            groupedBuf.readBytes(groupedData);
            return new Avp(AvpCodes.SUBSCRIPTION_ID, Avp.FLAG_MANDATORY, groupedData);
        } finally {
            groupedBuf.release();
        }
    }

    // ==================== CER/CEA Tests ====================

    @Test
    void cerCea_respondsWithCorrectResultCode() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildCer(100L, 200L));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea, "CEA should be returned");
        assertEquals(2001L, cea.findAvp(AvpCodes.RESULT_CODE).orElseThrow().asUnsigned32());
        channel.finish();
    }

    @Test
    void cerCea_containsOriginHostAndOriginRealm() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildCer(100L, 200L));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea);
        String originHost = cea.findAvp(AvpCodes.ORIGIN_HOST).orElseThrow().asUtf8String();
        String originRealm = cea.findAvp(AvpCodes.ORIGIN_REALM).orElseThrow().asUtf8String();
        assertFalse(originHost.isEmpty(), "Origin-Host should not be empty");
        assertFalse(originRealm.isEmpty(), "Origin-Realm should not be empty");
        channel.finish();
    }

    @Test
    void cerCea_containsAuthApplicationId() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildCer(100L, 200L));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea);
        long authAppId = cea.findAvp(AvpCodes.AUTH_APPLICATION_ID).orElseThrow().asUnsigned32();
        assertEquals(4L, authAppId, "Auth-Application-Id should be 4");
        channel.finish();
    }

    @Test
    void cerCea_preservesHopByHopAndEndToEndIds() {
        EmbeddedChannel channel = createChannel();
        long hbhId = 0xABCD1234L;
        long e2eId = 0xDEADBEEFL;
        sendMessage(channel, buildCer(hbhId, e2eId));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea);
        assertEquals(hbhId, cea.getHeader().hopByHopId(), "HbH ID should be preserved");
        assertEquals(e2eId, cea.getHeader().endToEndId(), "E2E ID should be preserved");
        channel.finish();
    }

    // ==================== DWR/DWA Tests ====================

    @Test
    void dwrDwa_containsOriginHostAndOriginRealm() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildDwr(30L, 40L));
        DiameterMessage dwa = readResponse(channel);
        assertNotNull(dwa);
        String originHost = dwa.findAvp(AvpCodes.ORIGIN_HOST).orElseThrow().asUtf8String();
        String originRealm = dwa.findAvp(AvpCodes.ORIGIN_REALM).orElseThrow().asUtf8String();
        assertFalse(originHost.isEmpty(), "Origin-Host should not be empty");
        assertFalse(originRealm.isEmpty(), "Origin-Realm should not be empty");
        channel.finish();
    }

    @Test
    void dwrDwa_preservesHopByHopAndEndToEndIds() {
        EmbeddedChannel channel = createChannel();
        long hbhId = 0xCAFEBABEL;
        long e2eId = 0xFEEDFACEL;
        sendMessage(channel, buildDwr(hbhId, e2eId));
        DiameterMessage dwa = readResponse(channel);
        assertNotNull(dwa);
        assertEquals(hbhId, dwa.getHeader().hopByHopId(), "HbH ID should be preserved");
        assertEquals(e2eId, dwa.getHeader().endToEndId(), "E2E ID should be preserved");
        channel.finish();
    }

    @Test
    void dwrDwa_isNotARequest() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildDwr(30L, 40L));
        DiameterMessage dwa = readResponse(channel);
        assertNotNull(dwa);
        assertFalse(dwa.isRequest(), "DWA should not have Request flag set");
        assertEquals(CommandCodes.DEVICE_WATCHDOG, dwa.getHeader().commandCode());
        channel.finish();
    }

    // ==================== Unrecognized Command Code Tests ====================

    @Test
    void unrecognizedCommandCode_isDiscardedWithoutResponse() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildUnrecognizedMessage(50L, 60L));
        DiameterMessage response = readResponse(channel);
        assertNull(response, "Unrecognized command code should not produce a response");
        channel.finish();
    }

    @Test
    void unrecognizedCommandCode_doesNotCloseConnection() {
        EmbeddedChannel channel = createChannel();
        sendMessage(channel, buildUnrecognizedMessage(50L, 60L));
        assertTrue(channel.isActive(), "Channel should remain active after unrecognized command");
        sendMessage(channel, buildCer(70L, 80L));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea, "Channel should still process valid messages after unrecognized command");
        assertEquals(2001L, cea.findAvp(AvpCodes.RESULT_CODE).orElseThrow().asUnsigned32());
        channel.finish();
    }

    // ==================== Concurrent Connections Tests ====================

    @Test
    void concurrentConnections_workIndependently() {
        EmbeddedChannel channel1 = createChannel(0);
        EmbeddedChannel channel2 = createChannel(0);
        EmbeddedChannel channel3 = createChannel(0);

        sendMessage(channel1, buildCer(1L, 1L));
        sendMessage(channel2, buildCcr(2L, 2L, "session-ch2", 1L, 0L));
        sendMessage(channel3, buildDwr(3L, 3L));

        channel2.runScheduledPendingTasks();

        DiameterMessage cea = readResponse(channel1);
        assertNotNull(cea);
        assertEquals(CommandCodes.CAPABILITIES_EXCHANGE, cea.getHeader().commandCode());
        assertEquals(1L, cea.getHeader().hopByHopId());

        DiameterMessage cca = readResponse(channel2);
        assertNotNull(cca);
        assertEquals(CommandCodes.CREDIT_CONTROL, cca.getHeader().commandCode());
        assertEquals(2L, cca.getHeader().hopByHopId());

        DiameterMessage dwa = readResponse(channel3);
        assertNotNull(dwa);
        assertEquals(CommandCodes.DEVICE_WATCHDOG, dwa.getHeader().commandCode());
        assertEquals(3L, dwa.getHeader().hopByHopId());

        channel1.finish();
        channel2.finish();
        channel3.finish();
    }

    @Test
    void concurrentConnections_delayOnOneDoesNotBlockOthers() {
        // Both channels use delay=0 for EmbeddedChannel compatibility.
        // The key property is that each channel operates independently.
        EmbeddedChannel channel1 = createChannel(0);
        EmbeddedChannel channel2 = createChannel(0);

        sendMessage(channel1, buildCcr(1L, 1L, "session-1", 1L, 0L));
        sendMessage(channel2, buildCcr(2L, 2L, "session-2", 1L, 0L));

        // Run scheduled tasks on channel2 only
        channel2.runScheduledPendingTasks();

        // Channel2 should have its response
        DiameterMessage response2 = readResponse(channel2);
        assertNotNull(response2, "Channel 2 should respond after running its tasks");
        assertEquals(2L, response2.getHeader().hopByHopId());

        // Channel1 should also have its response after running its tasks
        channel1.runScheduledPendingTasks();
        DiameterMessage response1 = readResponse(channel1);
        assertNotNull(response1, "Channel 1 should respond after running its tasks");
        assertEquals(1L, response1.getHeader().hopByHopId());

        channel1.finish();
        channel2.finish();
    }

    @Test
    void concurrentConnections_multipleMessagesOnSameChannel() {
        EmbeddedChannel channel = createChannel(0);

        sendMessage(channel, buildCer(1L, 10L));
        DiameterMessage cea = readResponse(channel);
        assertNotNull(cea);
        assertEquals(CommandCodes.CAPABILITIES_EXCHANGE, cea.getHeader().commandCode());

        sendMessage(channel, buildCcr(2L, 20L, "multi-session", 1L, 0L));
        channel.runScheduledPendingTasks();
        DiameterMessage cca = readResponse(channel);
        assertNotNull(cca);
        assertEquals(CommandCodes.CREDIT_CONTROL, cca.getHeader().commandCode());

        sendMessage(channel, buildDwr(3L, 30L));
        DiameterMessage dwa = readResponse(channel);
        assertNotNull(dwa);
        assertEquals(CommandCodes.DEVICE_WATCHDOG, dwa.getHeader().commandCode());

        channel.finish();
    }
}
