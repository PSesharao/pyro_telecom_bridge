package com.telecombridge.simulator;

import com.telecombridge.codec.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Tag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Simulator Echo Property (Property 15).
 * <p>
 * **Validates: Requirements 8.2, 8.3, 8.4**
 * <p>
 * For any Diameter request (CER, CCR, or DWR) sent to the Simulator,
 * the corresponding answer (CEA, CCA, or DWA) SHALL preserve the
 * Hop-by-Hop-ID and End-to-End-ID from the request, and SHALL contain
 * Result_Code 2001. For CCR specifically, the CCA SHALL also echo
 * Session-Id, CC-Request-Type, CC-Request-Number, and Subscription-Id AVPs.
 */
@Tag("Feature: telecom-bridge, Property 15: Simulator Echo Property")
class SimulatorEchoPropertyTest {

    private static final long RESULT_CODE_SUCCESS = 2001L;

    /**
     * **Validates: Requirements 8.2**
     * <p>
     * For any CER with random HbH and E2E IDs, the CEA preserves both IDs
     * and contains Result_Code 2001.
     */
    @Property(tries = 100)
    void cerResponsePreservesIdsAndContainsResultCode(
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long hopByHopId,
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long endToEndId) {

        EmbeddedChannel channel = createChannel();
        try {
            // Build CER (Command Code 257, Request flag set)
            DiameterHeader cerHeader = new DiameterHeader(
                    DiameterHeader.DIAMETER_VERSION,
                    0, // placeholder
                    DiameterHeader.FLAG_REQUEST,
                    CommandCodes.CAPABILITIES_EXCHANGE,
                    0L, // Application-ID 0 for base protocol
                    hopByHopId,
                    endToEndId
            );
            DiameterMessage cer = new DiameterMessage(cerHeader);
            cer.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, "TEST-CLIENT"));
            cer.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, "test.local"));

            // Encode and send
            ByteBuf encoded = DiameterCodec.encode(cer);
            channel.writeInbound(encoded);

            // Read the response
            Object outMsg = channel.readOutbound();
            assertNotNull(outMsg, "Expected CEA response but got null");
            assertTrue(outMsg instanceof ByteBuf, "Expected ByteBuf output");

            ByteBuf responseBuf = (ByteBuf) outMsg;
            DiameterMessage cea = DiameterCodec.decode(responseBuf);
            responseBuf.release();

            // Verify answer flag (Request bit cleared)
            assertFalse(cea.isRequest(), "CEA should not have Request flag set");

            // Verify command code preserved
            assertEquals(CommandCodes.CAPABILITIES_EXCHANGE, cea.getHeader().commandCode(),
                    "CEA should have command code 257");

            // Verify HbH and E2E IDs preserved
            assertEquals(hopByHopId, cea.getHeader().hopByHopId(),
                    "CEA Hop-by-Hop-ID should match CER");
            assertEquals(endToEndId, cea.getHeader().endToEndId(),
                    "CEA End-to-End-ID should match CER");

            // Verify Result_Code 2001
            Optional<Avp> resultCodeAvp = cea.findAvp(AvpCodes.RESULT_CODE);
            assertTrue(resultCodeAvp.isPresent(), "CEA should contain Result_Code AVP");
            assertEquals(RESULT_CODE_SUCCESS, resultCodeAvp.get().asUnsigned32(),
                    "CEA Result_Code should be 2001");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * **Validates: Requirements 8.4**
     * <p>
     * For any DWR with random HbH and E2E IDs, the DWA preserves both IDs
     * and contains Result_Code 2001.
     */
    @Property(tries = 100)
    void dwrResponsePreservesIdsAndContainsResultCode(
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long hopByHopId,
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long endToEndId) {

        EmbeddedChannel channel = createChannel();
        try {
            // Build DWR (Command Code 280, Request flag set)
            DiameterHeader dwrHeader = new DiameterHeader(
                    DiameterHeader.DIAMETER_VERSION,
                    0, // placeholder
                    DiameterHeader.FLAG_REQUEST,
                    CommandCodes.DEVICE_WATCHDOG,
                    0L, // Application-ID 0 for base protocol
                    hopByHopId,
                    endToEndId
            );
            DiameterMessage dwr = new DiameterMessage(dwrHeader);
            dwr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, "TEST-CLIENT"));
            dwr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, "test.local"));

            // Encode and send
            ByteBuf encoded = DiameterCodec.encode(dwr);
            channel.writeInbound(encoded);

            // Read the response
            Object outMsg = channel.readOutbound();
            assertNotNull(outMsg, "Expected DWA response but got null");
            assertTrue(outMsg instanceof ByteBuf, "Expected ByteBuf output");

            ByteBuf responseBuf = (ByteBuf) outMsg;
            DiameterMessage dwa = DiameterCodec.decode(responseBuf);
            responseBuf.release();

            // Verify answer flag (Request bit cleared)
            assertFalse(dwa.isRequest(), "DWA should not have Request flag set");

            // Verify command code preserved
            assertEquals(CommandCodes.DEVICE_WATCHDOG, dwa.getHeader().commandCode(),
                    "DWA should have command code 280");

            // Verify HbH and E2E IDs preserved
            assertEquals(hopByHopId, dwa.getHeader().hopByHopId(),
                    "DWA Hop-by-Hop-ID should match DWR");
            assertEquals(endToEndId, dwa.getHeader().endToEndId(),
                    "DWA End-to-End-ID should match DWR");

            // Verify Result_Code 2001
            Optional<Avp> resultCodeAvp = dwa.findAvp(AvpCodes.RESULT_CODE);
            assertTrue(resultCodeAvp.isPresent(), "DWA should contain Result_Code AVP");
            assertEquals(RESULT_CODE_SUCCESS, resultCodeAvp.get().asUnsigned32(),
                    "DWA Result_Code should be 2001");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * **Validates: Requirements 8.3**
     * <p>
     * For any CCR with random HbH and E2E IDs and random AVP values,
     * the CCA preserves both IDs, contains Result_Code 2001, and echoes
     * Session-Id, CC-Request-Type, CC-Request-Number, and Subscription-Id.
     */
    @Property(tries = 100)
    void ccrResponsePreservesIdsAndEchoesAvps(
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long hopByHopId,
            @ForAll @LongRange(min = 0, max = 0xFFFFFFFFL) long endToEndId,
            @ForAll("sessionIds") String sessionId,
            @ForAll @IntRange(min = 1, max = 4) int ccRequestType,
            @ForAll @IntRange(min = 0, max = 999999) int ccRequestNumber,
            @ForAll("msisdns") String subscriptionIdData) {

        // Use delay=0 so the response is immediate in the EmbeddedChannel
        EmbeddedChannel channel = createChannelWithDelay(0);
        try {
            // Build CCR (Command Code 272, Request + Proxiable flags set)
            byte flags = (byte) (DiameterHeader.FLAG_REQUEST | DiameterHeader.FLAG_PROXIABLE);
            DiameterHeader ccrHeader = new DiameterHeader(
                    DiameterHeader.DIAMETER_VERSION,
                    0, // placeholder
                    flags,
                    CommandCodes.CREDIT_CONTROL,
                    4L, // Application-ID 4 for Credit-Control
                    hopByHopId,
                    endToEndId
            );
            DiameterMessage ccr = new DiameterMessage(ccrHeader);

            // Add Session-Id
            ccr.addAvp(buildUtf8StringAvp(AvpCodes.SESSION_ID, sessionId));
            // Add CC-Request-Type
            ccr.addAvp(buildUnsigned32Avp(AvpCodes.CC_REQUEST_TYPE, ccRequestType));
            // Add CC-Request-Number
            ccr.addAvp(buildUnsigned32Avp(AvpCodes.CC_REQUEST_NUMBER, ccRequestNumber));
            // Add Subscription-Id (grouped AVP)
            ccr.addAvp(buildSubscriptionIdAvp(subscriptionIdData));
            // Add Origin-Host and Origin-Realm
            ccr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, "TEST-CLIENT"));
            ccr.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, "test.local"));

            // Encode and send
            ByteBuf encoded = DiameterCodec.encode(ccr);
            channel.writeInbound(encoded);

            // Run any scheduled tasks (the handler uses ctx.executor().schedule())
            channel.runScheduledPendingTasks();

            // Read the response
            Object outMsg = channel.readOutbound();
            assertNotNull(outMsg, "Expected CCA response but got null");
            assertTrue(outMsg instanceof ByteBuf, "Expected ByteBuf output");

            ByteBuf responseBuf = (ByteBuf) outMsg;
            DiameterMessage cca = DiameterCodec.decode(responseBuf);
            responseBuf.release();

            // Verify answer flag (Request bit cleared)
            assertFalse(cca.isRequest(), "CCA should not have Request flag set");

            // Verify command code preserved
            assertEquals(CommandCodes.CREDIT_CONTROL, cca.getHeader().commandCode(),
                    "CCA should have command code 272");

            // Verify HbH and E2E IDs preserved
            assertEquals(hopByHopId, cca.getHeader().hopByHopId(),
                    "CCA Hop-by-Hop-ID should match CCR");
            assertEquals(endToEndId, cca.getHeader().endToEndId(),
                    "CCA End-to-End-ID should match CCR");

            // Verify Result_Code 2001
            Optional<Avp> resultCodeAvp = cca.findAvp(AvpCodes.RESULT_CODE);
            assertTrue(resultCodeAvp.isPresent(), "CCA should contain Result_Code AVP");
            assertEquals(RESULT_CODE_SUCCESS, resultCodeAvp.get().asUnsigned32(),
                    "CCA Result_Code should be 2001");

            // Verify Session-Id echoed
            Optional<Avp> sessionIdAvp = cca.findAvp(AvpCodes.SESSION_ID);
            assertTrue(sessionIdAvp.isPresent(), "CCA should echo Session-Id AVP");
            assertEquals(sessionId, sessionIdAvp.get().asUtf8String(),
                    "CCA Session-Id should match CCR");

            // Verify CC-Request-Type echoed
            Optional<Avp> ccRequestTypeAvp = cca.findAvp(AvpCodes.CC_REQUEST_TYPE);
            assertTrue(ccRequestTypeAvp.isPresent(), "CCA should echo CC-Request-Type AVP");
            assertEquals((long) ccRequestType, ccRequestTypeAvp.get().asUnsigned32(),
                    "CCA CC-Request-Type should match CCR");

            // Verify CC-Request-Number echoed
            Optional<Avp> ccRequestNumberAvp = cca.findAvp(AvpCodes.CC_REQUEST_NUMBER);
            assertTrue(ccRequestNumberAvp.isPresent(), "CCA should echo CC-Request-Number AVP");
            assertEquals((long) ccRequestNumber, ccRequestNumberAvp.get().asUnsigned32(),
                    "CCA CC-Request-Number should match CCR");

            // Verify Subscription-Id echoed
            List<Avp> subscriptionIdAvps = cca.findAllAvps(AvpCodes.SUBSCRIPTION_ID);
            assertFalse(subscriptionIdAvps.isEmpty(), "CCA should echo Subscription-Id AVP");
            // Verify the subscription data matches
            Avp subIdAvp = subscriptionIdAvps.get(0);
            List<Avp> nestedAvps = subIdAvp.asGrouped();
            Optional<Avp> subIdDataAvp = nestedAvps.stream()
                    .filter(a -> a.getCode() == AvpCodes.SUBSCRIPTION_ID_DATA)
                    .findFirst();
            assertTrue(subIdDataAvp.isPresent(), "Subscription-Id should contain Subscription-Id-Data");
            assertEquals(subscriptionIdData, subIdDataAvp.get().asUtf8String(),
                    "Subscription-Id-Data should match CCR");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(50)
                .map(s -> "TEST-CLIENT;" + s + ";1");
    }

    @Provide
    Arbitrary<String> msisdns() {
        return Arbitraries.strings()
                .numeric()
                .ofMinLength(8).ofMaxLength(15)
                .map(s -> "+" + s);
    }

    // --- Helper methods ---

    private EmbeddedChannel createChannel() {
        return createChannelWithDelay(0);
    }

    private EmbeddedChannel createChannelWithDelay(int delayMs) {
        return new EmbeddedChannel(
                new MessageFrameDecoder(),
                new SimulatorHandler(delayMs)
        );
    }

    private Avp buildUtf8StringAvp(int code, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }

    private Avp buildUnsigned32Avp(int code, long value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }

    private Avp buildSubscriptionIdAvp(String msisdn) {
        // Build nested AVPs: Subscription-Id-Type (0 = END_USER_E164) and Subscription-Id-Data
        Avp typeAvp = buildUnsigned32Avp(AvpCodes.SUBSCRIPTION_ID_TYPE, 0L);
        Avp dataAvp = buildUtf8StringAvp(AvpCodes.SUBSCRIPTION_ID_DATA, msisdn);

        // Encode nested AVPs into grouped data
        io.netty.buffer.ByteBuf nestedBuf = io.netty.buffer.Unpooled.buffer();
        try {
            AvpEncoder.encode(typeAvp, nestedBuf);
            AvpEncoder.encode(dataAvp, nestedBuf);
            byte[] groupedData = new byte[nestedBuf.readableBytes()];
            nestedBuf.readBytes(groupedData);
            return new Avp(AvpCodes.SUBSCRIPTION_ID, Avp.FLAG_MANDATORY, groupedData);
        } finally {
            nestedBuf.release();
        }
    }
}
