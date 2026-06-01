package com.telecombridge.simulator;

import com.telecombridge.codec.Avp;
import com.telecombridge.codec.AvpCodes;
import com.telecombridge.codec.CommandCodes;
import com.telecombridge.codec.DiameterCodec;
import com.telecombridge.codec.DiameterHeader;
import com.telecombridge.codec.DiameterMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Netty channel handler for the Diameter Simulator.
 * <p>
 * Dispatches incoming Diameter messages based on command code:
 * <ul>
 *   <li>257 (CER) → responds with CEA</li>
 *   <li>272 (CCR) → responds with CCA after configurable delay</li>
 *   <li>280 (DWR) → responds with DWA</li>
 *   <li>Other → logs WARN and discards</li>
 * </ul>
 */
public class SimulatorHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatorHandler.class);

    private static final String ORIGIN_HOST = "SIM-SERVER";
    private static final String ORIGIN_REALM = "sim.local";
    private static final long RESULT_CODE_SUCCESS = 2001L;
    private static final long AUTH_APPLICATION_ID = 4L;

    private final int delayMs;

    /**
     * Creates a new SimulatorHandler with the specified response delay for CCR messages.
     *
     * @param delayMs the simulated delay in milliseconds before sending CCA
     */
    public SimulatorHandler(int delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof DiameterMessage request)) {
            return;
        }

        int commandCode = request.getHeader().commandCode();

        switch (commandCode) {
            case CommandCodes.CAPABILITIES_EXCHANGE -> handleCer(ctx, request);
            case CommandCodes.CREDIT_CONTROL -> handleCcr(ctx, request);
            case CommandCodes.DEVICE_WATCHDOG -> handleDwr(ctx, request);
            default -> log.warn("Unrecognized Command Code: {}. Discarding message.", commandCode);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("Client connected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Client disconnected: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in simulator handler for {}: {}",
                ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * Handles CER (Capabilities-Exchange-Request) by responding with CEA.
     * <p>
     * CEA contains: Result_Code 2001, Origin-Host, Origin-Realm, Auth-Application-Id (4).
     * Preserves Hop-by-Hop-ID and End-to-End-ID from the request.
     */
    private void handleCer(ChannelHandlerContext ctx, DiameterMessage request) {
        log.debug("Received CER from {}, HbH={}, E2E={}",
                ctx.channel().remoteAddress(),
                request.getHeader().hopByHopId(),
                request.getHeader().endToEndId());

        DiameterMessage cea = buildAnswer(request, CommandCodes.CAPABILITIES_EXCHANGE, 0L);

        // Add Result-Code
        cea.addAvp(buildUnsigned32Avp(AvpCodes.RESULT_CODE, RESULT_CODE_SUCCESS));
        // Add Origin-Host
        cea.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, ORIGIN_HOST));
        // Add Origin-Realm
        cea.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, ORIGIN_REALM));
        // Add Auth-Application-Id
        cea.addAvp(buildUnsigned32Avp(AvpCodes.AUTH_APPLICATION_ID, AUTH_APPLICATION_ID));

        sendResponse(ctx, cea);

        log.debug("Sent CEA to {}, HbH={}, E2E={}",
                ctx.channel().remoteAddress(),
                cea.getHeader().hopByHopId(),
                cea.getHeader().endToEndId());
    }

    /**
     * Handles CCR (Credit-Control-Request) by responding with CCA after a configurable delay.
     * <p>
     * CCA contains: Result_Code 2001, echoed Session-Id, CC-Request-Type,
     * CC-Request-Number, and Subscription-Id. Preserves HbH and E2E IDs.
     * <p>
     * Uses ctx.executor().schedule() to ensure the delay on one connection
     * does not block other connections.
     */
    private void handleCcr(ChannelHandlerContext ctx, DiameterMessage request) {
        log.debug("Received CCR from {}, HbH={}, E2E={}",
                ctx.channel().remoteAddress(),
                request.getHeader().hopByHopId(),
                request.getHeader().endToEndId());

        // Schedule the response after the configured delay
        ctx.executor().schedule(() -> {
            DiameterMessage cca = buildAnswer(request, CommandCodes.CREDIT_CONTROL, AUTH_APPLICATION_ID);

            // Add Result-Code
            cca.addAvp(buildUnsigned32Avp(AvpCodes.RESULT_CODE, RESULT_CODE_SUCCESS));

            // Echo Session-Id
            request.findAvp(AvpCodes.SESSION_ID).ifPresent(cca::addAvp);

            // Echo CC-Request-Type
            request.findAvp(AvpCodes.CC_REQUEST_TYPE).ifPresent(cca::addAvp);

            // Echo CC-Request-Number
            request.findAvp(AvpCodes.CC_REQUEST_NUMBER).ifPresent(cca::addAvp);

            // Echo Subscription-Id
            request.findAllAvps(AvpCodes.SUBSCRIPTION_ID).forEach(cca::addAvp);

            sendResponse(ctx, cca);

            log.debug("Sent CCA to {}, HbH={}, E2E={}",
                    ctx.channel().remoteAddress(),
                    cca.getHeader().hopByHopId(),
                    cca.getHeader().endToEndId());
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles DWR (Device-Watchdog-Request) by responding with DWA.
     * <p>
     * DWA contains: Result_Code 2001, Origin-Host, Origin-Realm.
     * Preserves HbH and E2E IDs.
     */
    private void handleDwr(ChannelHandlerContext ctx, DiameterMessage request) {
        log.debug("Received DWR from {}, HbH={}, E2E={}",
                ctx.channel().remoteAddress(),
                request.getHeader().hopByHopId(),
                request.getHeader().endToEndId());

        DiameterMessage dwa = buildAnswer(request, CommandCodes.DEVICE_WATCHDOG, 0L);

        // Add Result-Code
        dwa.addAvp(buildUnsigned32Avp(AvpCodes.RESULT_CODE, RESULT_CODE_SUCCESS));
        // Add Origin-Host
        dwa.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_HOST, ORIGIN_HOST));
        // Add Origin-Realm
        dwa.addAvp(buildUtf8StringAvp(AvpCodes.ORIGIN_REALM, ORIGIN_REALM));

        sendResponse(ctx, dwa);

        log.debug("Sent DWA to {}, HbH={}, E2E={}",
                ctx.channel().remoteAddress(),
                dwa.getHeader().hopByHopId(),
                dwa.getHeader().endToEndId());
    }

    /**
     * Builds an answer message from a request, preserving HbH and E2E IDs.
     * The Request flag is cleared in the answer.
     */
    private DiameterMessage buildAnswer(DiameterMessage request, int commandCode, long applicationId) {
        DiameterHeader requestHeader = request.getHeader();

        // Clear the Request flag (0x80) from command flags for the answer
        byte answerFlags = (byte) (requestHeader.commandFlags() & ~DiameterHeader.FLAG_REQUEST);

        DiameterHeader answerHeader = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // placeholder — will be calculated during encoding
                answerFlags,
                commandCode,
                applicationId,
                requestHeader.hopByHopId(),
                requestHeader.endToEndId()
        );

        return new DiameterMessage(answerHeader);
    }

    /**
     * Encodes and sends a Diameter response message.
     */
    private void sendResponse(ChannelHandlerContext ctx, DiameterMessage response) {
        ByteBuf encoded = DiameterCodec.encode(response);
        ctx.writeAndFlush(encoded);
    }

    /**
     * Builds an Unsigned32 AVP with the Mandatory flag set.
     */
    private Avp buildUnsigned32Avp(int code, long value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }

    /**
     * Builds a UTF8String AVP with the Mandatory flag set.
     */
    private Avp buildUtf8StringAvp(int code, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        return new Avp(code, Avp.FLAG_MANDATORY, data);
    }
}
