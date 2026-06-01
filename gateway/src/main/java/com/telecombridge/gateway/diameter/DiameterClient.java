package com.telecombridge.gateway.diameter;

import com.telecombridge.codec.Avp;
import com.telecombridge.codec.AvpCodes;
import com.telecombridge.codec.CommandCodes;
import com.telecombridge.codec.DiameterCodec;
import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterHeader;
import com.telecombridge.codec.DiameterMessage;
import com.telecombridge.codec.DiameterProtocolException;
import com.telecombridge.codec.MessageFrameDecoder;
import com.telecombridge.gateway.config.DiameterProperties;
import com.telecombridge.gateway.dto.GrantedServiceUnit;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Diameter protocol client using Netty for asynchronous TCP communication.
 * <p>
 * Manages the TCP connection lifecycle to the Diameter server, including
 * connection establishment, CER/CEA capability exchange, exponential backoff
 * reconnection, message sending, and graceful shutdown.
 * <p>
 * Connection states:
 * <ul>
 *   <li>DISCONNECTED — no active connection</li>
 *   <li>CONNECTING — TCP connection in progress</li>
 *   <li>CER_SENT — TCP connected, CER sent, awaiting CEA</li>
 *   <li>READY — CEA received with Result_Code 2001, ready for CCR traffic</li>
 * </ul>
 */
@Component
public class DiameterClient {

    private static final Logger log = LoggerFactory.getLogger(DiameterClient.class);

    /** Initial reconnection delay in milliseconds. */
    private static final long INITIAL_BACKOFF_MS = 1000;

    /** Maximum reconnection delay in milliseconds. */
    private static final long MAX_BACKOFF_MS = 30000;

    /**
     * Connection states for the Diameter client lifecycle.
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CER_SENT,
        READY
    }

    private final DiameterProperties properties;
    private final IdGenerator idGenerator;
    private final RequestCorrelator correlator;
    private final WatchdogScheduler watchdogScheduler;
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);

    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;
    private volatile Channel channel;
    private volatile boolean shutdownRequested = false;
    private volatile long currentBackoffMs = INITIAL_BACKOFF_MS;

    public DiameterClient(DiameterProperties properties, IdGenerator idGenerator,
                          RequestCorrelator correlator, @Lazy WatchdogScheduler watchdogScheduler) {
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.correlator = correlator;
        this.watchdogScheduler = watchdogScheduler;
    }

    /**
     * Initiates connection to the Diameter server on application startup.
     */
    @PostConstruct
    public void init() {
        workerGroup = new NioEventLoopGroup(properties.getThreadPoolSize());
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new MessageFrameDecoder());
                        ch.pipeline().addLast(new DiameterMessageHandler());
                    }
                });

        connect();
    }

    /**
     * Attempts to establish a TCP connection to the configured Diameter server.
     * <p>
     * On success, sends a CER message to initiate capability exchange.
     * On failure, schedules reconnection with exponential backoff.
     *
     * @return a CompletableFuture that completes when the connection attempt finishes
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (shutdownRequested) {
            future.completeExceptionally(new DiameterConnectionException("Shutdown requested"));
            return future;
        }

        if (!state.compareAndSet(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
            ConnectionState current = state.get();
            if (current == ConnectionState.CONNECTING || current == ConnectionState.CER_SENT
                    || current == ConnectionState.READY) {
                future.complete(null);
                return future;
            }
        }

        log.info("event=connecting remoteHost={}:{}", properties.getHost(), properties.getPort());

        ChannelFuture connectFuture = bootstrap.connect(properties.getHost(), properties.getPort());
        connectFuture.addListener(f -> {
            if (f.isSuccess()) {
                channel = connectFuture.channel();
                log.info("event=tcp_connected remoteHost={}:{}", properties.getHost(), properties.getPort());
                sendCer();
                future.complete(null);
            } else {
                log.error("event=connect_failure category=connection_error remoteHost={}:{} cause={}",
                        properties.getHost(), properties.getPort(),
                        f.cause() != null ? f.cause().getMessage() : "unknown error");
                state.set(ConnectionState.DISCONNECTED);
                scheduleReconnect();
                future.completeExceptionally(new DiameterConnectionException(
                        "Failed to connect to Diameter server at " + properties.getHost() + ":" + properties.getPort(),
                        f.cause()));
            }
        });

        return future;
    }

    /**
     * Sends a CER (Capabilities-Exchange-Request) message after TCP connection is established.
     * Contains Origin-Host, Origin-Realm, Host-IP-Address, Vendor-Id, Product-Name,
     * and Auth-Application-Id (4) AVPs.
     */
    private void sendCer() {
        state.set(ConnectionState.CER_SENT);

        List<Avp> avps = new ArrayList<>();

        // Origin-Host (264) - UTF8String, Mandatory
        byte[] originHostBytes = properties.getOriginHost().getBytes(StandardCharsets.UTF_8);
        avps.add(new Avp(AvpCodes.ORIGIN_HOST, Avp.FLAG_MANDATORY, originHostBytes));

        // Origin-Realm (296) - UTF8String, Mandatory
        byte[] originRealmBytes = properties.getOriginRealm().getBytes(StandardCharsets.UTF_8);
        avps.add(new Avp(AvpCodes.ORIGIN_REALM, Avp.FLAG_MANDATORY, originRealmBytes));

        // Host-IP-Address (257) - Address type (2 bytes address family + 4 bytes IPv4)
        byte[] hostIpData = encodeHostIpAddress();
        avps.add(new Avp(AvpCodes.HOST_IP_ADDRESS, Avp.FLAG_MANDATORY, hostIpData));

        // Vendor-Id (266) - Unsigned32, Mandatory
        byte[] vendorIdData = encodeUnsigned32(0);
        avps.add(new Avp(AvpCodes.VENDOR_ID, Avp.FLAG_MANDATORY, vendorIdData));

        // Product-Name (269) - UTF8String
        byte[] productNameBytes = "Telecom-Bridge".getBytes(StandardCharsets.UTF_8);
        avps.add(new Avp(AvpCodes.PRODUCT_NAME, (byte) 0, productNameBytes));

        // Auth-Application-Id (258) - Unsigned32, Mandatory, value = 4
        byte[] authAppIdData = encodeUnsigned32(4);
        avps.add(new Avp(AvpCodes.AUTH_APPLICATION_ID, Avp.FLAG_MANDATORY, authAppIdData));

        // Build CER header: Command Code 257, App-ID 0, Request flag set
        long hopByHopId = idGenerator.nextHopByHopId();
        long endToEndId = idGenerator.nextEndToEndId();
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // message length will be calculated by encoder
                DiameterHeader.FLAG_REQUEST, // Request flag only for CER
                CommandCodes.CAPABILITIES_EXCHANGE,
                0, // Application-ID 0 for CER
                hopByHopId,
                endToEndId
        );

        DiameterMessage cer = new DiameterMessage(header, avps);

        Channel ch = this.channel;
        if (ch != null && ch.isActive()) {
            ByteBuf encoded = DiameterCodec.encode(cer);
            ch.writeAndFlush(encoded).addListener(f -> {
                if (f.isSuccess()) {
                    log.debug("event=diameter_exchange type=CER hopByHopId={} endToEndId={}", hopByHopId, endToEndId);
                } else {
                    log.error("event=cer_send_failure category=connection_error remoteHost={} cause={}",
                            properties.getHost() + ":" + properties.getPort(),
                            f.cause() != null ? f.cause().getMessage() : "unknown error");
                    state.set(ConnectionState.DISCONNECTED);
                    scheduleReconnect();
                }
            });
        } else {
            log.error("event=cer_send_failure category=connection_error cause=channel_not_active");
            state.set(ConnectionState.DISCONNECTED);
            scheduleReconnect();
        }
    }

    /**
     * Schedules a reconnection attempt using exponential backoff.
     * Starts at 1 second, doubles on each failure, caps at 30 seconds.
     * Resets to 1 second on successful reconnection.
     */
    void scheduleReconnect() {
        if (shutdownRequested) {
            log.info("Shutdown requested, not scheduling reconnection");
            return;
        }

        long delay = currentBackoffMs;
        currentBackoffMs = Math.min(currentBackoffMs * 2, MAX_BACKOFF_MS);

        log.info("Scheduling reconnection in {}ms (next backoff: {}ms)", delay, currentBackoffMs);

        workerGroup.schedule(() -> {
            if (!shutdownRequested) {
                state.set(ConnectionState.DISCONNECTED);
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the backoff delay to the initial value.
     * Called when a connection is successfully established (CEA with Result_Code 2001).
     */
    private void resetBackoff() {
        currentBackoffMs = INITIAL_BACKOFF_MS;
    }

    /**
     * Sends a Diameter message to the connected server.
     *
     * @param message the Diameter message to send
     * @return a CompletableFuture that completes when the write operation finishes
     * @throws DiameterConnectionException if the connection is not established
     */
    public CompletableFuture<Void> send(DiameterMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Channel ch = this.channel;
        if (ch == null || !ch.isActive()) {
            future.completeExceptionally(new DiameterConnectionException(
                    "Cannot send message: connection is not active"));
            return future;
        }

        ByteBuf encoded = DiameterCodec.encode(message);
        ch.writeAndFlush(encoded).addListener(f -> {
            if (f.isSuccess()) {
                log.debug("event=diameter_send commandCode={} hopByHopId={} endToEndId={}",
                        message.getHeader().commandCode(),
                        message.getHeader().hopByHopId(),
                        message.getHeader().endToEndId());
                future.complete(null);
            } else {
                log.error("event=send_failure category=connection_error hopByHopId={} remoteHost={} cause={}",
                        message.getHeader().hopByHopId(),
                        properties.getHost() + ":" + properties.getPort(),
                        f.cause() != null ? f.cause().getMessage() : "unknown error");
                future.completeExceptionally(new DiameterConnectionException(
                        "Failed to send Diameter message", f.cause()));
            }
        });

        return future;
    }

    /**
     * Returns true if the connection is in the READY state and can accept CCR traffic.
     *
     * @return true if the client is ready to send CCR messages
     */
    public boolean isReady() {
        return state.get() == ConnectionState.READY;
    }

    /**
     * Returns the current connection state.
     *
     * @return the current {@link ConnectionState}
     */
    public ConnectionState getState() {
        return state.get();
    }

    /**
     * Sets the connection state. Used internally and by CER/CEA exchange logic.
     *
     * @param newState the new connection state
     */
    public void setState(ConnectionState newState) {
        state.set(newState);
    }

    /**
     * Returns the current backoff delay in milliseconds (for testing).
     */
    long getCurrentBackoffMs() {
        return currentBackoffMs;
    }

    /**
     * Gracefully shuts down the Diameter client, closing the channel and
     * releasing Netty resources.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Diameter client");
        shutdownRequested = true;
        state.set(ConnectionState.DISCONNECTED);
        watchdogScheduler.stop();

        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("Diameter client shutdown complete");
    }

    /**
     * Closes the current connection and initiates reconnection.
     * Called by the WatchdogScheduler when a DWA timeout occurs.
     */
    public void closeAndReconnect() {
        log.warn("Closing connection and initiating reconnection (watchdog timeout)");
        state.set(ConnectionState.DISCONNECTED);
        Channel ch = channel;
        channel = null;
        if (ch != null && ch.isActive()) {
            ch.close();
        }
        scheduleReconnect();
    }

    /**
     * Encodes a Host-IP-Address AVP value.
     * Format: 2 bytes address family (1 = IPv4) + 4 bytes IPv4 address.
     */
    private byte[] encodeHostIpAddress() {
        try {
            Channel ch = this.channel;
            byte[] address;
            if (ch != null && ch.localAddress() instanceof InetSocketAddress socketAddr) {
                address = socketAddr.getAddress().getAddress();
            } else {
                address = InetAddress.getLocalHost().getAddress();
            }
            // Address family 1 = IPv4
            byte[] result = new byte[2 + address.length];
            result[0] = 0x00;
            result[1] = 0x01; // IPv4
            System.arraycopy(address, 0, result, 2, address.length);
            return result;
        } catch (Exception e) {
            // Fallback to loopback
            return new byte[]{0x00, 0x01, 127, 0, 0, 1};
        }
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

    /**
     * Netty inbound handler for processing decoded Diameter messages.
     * <p>
     * Receives {@link DiameterMessage} objects from the pipeline (decoded by
     * {@link MessageFrameDecoder} + {@link DiameterCodec}) and dispatches them
     * based on command code.
     */
    private class DiameterMessageHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof DiameterMessage message)) {
                ctx.fireChannelRead(msg);
                return;
            }

            int commandCode = message.getHeader().commandCode();
            boolean isRequest = message.isRequest();

            log.debug("event=diameter_receive commandCode={} isRequest={} hopByHopId={} endToEndId={}",
                    commandCode, isRequest, message.getHeader().hopByHopId(), message.getHeader().endToEndId());

            switch (commandCode) {
                case CommandCodes.CAPABILITIES_EXCHANGE -> handleCea(message);
                case CommandCodes.CREDIT_CONTROL -> handleCca(message);
                case CommandCodes.DEVICE_WATCHDOG -> handleDwa(message);
                default -> log.warn("Received unrecognized command code: {}", commandCode);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.error("event=connection_lost category=connection_error remoteHost={}:{}",
                    properties.getHost(), properties.getPort());
            watchdogScheduler.stop();
            state.set(ConnectionState.DISCONNECTED);
            channel = null;
            scheduleReconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("event=channel_exception category=connection_error remoteHost={} cause={}",
                    properties.getHost() + ":" + properties.getPort(), cause.getMessage(), cause);
            ctx.close();
        }

        private void handleCea(DiameterMessage message) {
            log.debug("event=diameter_exchange type=CEA hopByHopId={} endToEndId={}",
                    message.getHeader().hopByHopId(), message.getHeader().endToEndId());

            // Extract Result_Code AVP
            Optional<Avp> resultCodeAvp = message.findAvp(AvpCodes.RESULT_CODE);
            if (resultCodeAvp.isEmpty()) {
                log.error("event=cea_failure category=protocol_error remoteHost={} cause=missing_result_code_avp",
                        properties.getHost() + ":" + properties.getPort());
                closeAndReconnect();
                return;
            }

            long resultCode = resultCodeAvp.get().asUnsigned32();
            if (resultCode == 2001) {
                // Success - mark connection as READY
                state.set(ConnectionState.READY);
                resetBackoff();
                log.info("event=connection_ready type=CEA resultCode=2001");
                // Start watchdog scheduler after successful CER/CEA exchange
                watchdogScheduler.start();
            } else {
                // Non-2001 result - close and reconnect
                log.error("event=cea_failure category=protocol_error remoteHost={} resultCode={} cause=non_success_result_code",
                        properties.getHost() + ":" + properties.getPort(), resultCode);
                closeAndReconnect();
            }
        }

        private void closeAndReconnect() {
            watchdogScheduler.stop();
            state.set(ConnectionState.DISCONNECTED);
            Channel ch = channel;
            channel = null;
            if (ch != null && ch.isActive()) {
                ch.close();
            }
            scheduleReconnect();
        }

        private void handleCca(DiameterMessage message) {
            long hopByHopId = message.getHeader().hopByHopId();
            long endToEndId = message.getHeader().endToEndId();
            log.debug("event=diameter_exchange type=CCA hopByHopId={} endToEndId={}",
                    hopByHopId, endToEndId);

            try {
                // Extract Session-Id
                Optional<Avp> sessionIdAvp = message.findAvp(AvpCodes.SESSION_ID);
                String sessionId = sessionIdAvp.map(Avp::asUtf8String).orElse("");

                // Extract Result_Code (mandatory)
                Optional<Avp> resultCodeAvp = message.findAvp(AvpCodes.RESULT_CODE);
                if (resultCodeAvp.isEmpty()) {
                    log.error("event=cca_error category=protocol_error hopByHopId={} remoteHost={} cause=missing_result_code_avp",
                            hopByHopId, properties.getHost() + ":" + properties.getPort());
                    correlator.completeExceptionally(hopByHopId,
                            new DiameterProtocolException("CCA missing Result_Code AVP"));
                    return;
                }
                long resultCode = resultCodeAvp.get().asUnsigned32();

                log.debug("event=diameter_exchange type=CCA hopByHopId={} endToEndId={} resultCode={}",
                        hopByHopId, endToEndId, resultCode);

                // Extract CC-Request-Type
                Optional<Avp> ccRequestTypeAvp = message.findAvp(AvpCodes.CC_REQUEST_TYPE);
                int ccRequestType = ccRequestTypeAvp.map(a -> (int) a.asUnsigned32()).orElse(0);

                // Extract CC-Request-Number
                Optional<Avp> ccRequestNumberAvp = message.findAvp(AvpCodes.CC_REQUEST_NUMBER);
                int ccRequestNumber = ccRequestNumberAvp.map(a -> (int) a.asUnsigned32()).orElse(0);

                // Extract Granted-Service-Unit (only if Result_Code == 2001)
                GrantedServiceUnit grantedServiceUnit = null;
                if (resultCode == 2001) {
                    Optional<Avp> gsuAvp = message.findAvp(AvpCodes.GRANTED_SERVICE_UNIT);
                    if (gsuAvp.isPresent()) {
                        grantedServiceUnit = parseGrantedServiceUnit(gsuAvp.get());
                    }
                }

                CcaData ccaData = new CcaData(sessionId, resultCode, ccRequestType,
                        ccRequestNumber, grantedServiceUnit);

                correlator.complete(hopByHopId, ccaData);

                log.debug("event=cca_correlated hopByHopId={} sessionId={} resultCode={}",
                        hopByHopId, sessionId, resultCode);
            } catch (Exception e) {
                log.error("event=cca_error category=processing_error hopByHopId={} remoteHost={} cause={}",
                        hopByHopId, properties.getHost() + ":" + properties.getPort(), e.getMessage());
                correlator.completeExceptionally(hopByHopId, e);
            }
        }

        private GrantedServiceUnit parseGrantedServiceUnit(Avp gsuAvp) {
            List<Avp> nested = gsuAvp.asGrouped();
            Long ccTime = null;
            Long ccTotalOctets = null;
            Long ccServiceSpecificUnits = null;

            for (Avp avp : nested) {
                switch (avp.getCode()) {
                    case AvpCodes.CC_TIME -> ccTime = avp.asUnsigned32();
                    case AvpCodes.CC_TOTAL_OCTETS -> ccTotalOctets = avp.asUnsigned32();
                    case AvpCodes.CC_SERVICE_SPECIFIC_UNITS -> ccServiceSpecificUnits = avp.asUnsigned32();
                }
            }

            return new GrantedServiceUnit(ccTime, ccTotalOctets, ccServiceSpecificUnits);
        }

        private void handleDwa(DiameterMessage message) {
            boolean isRequest = message.isRequest();
            if (isRequest) {
                // Incoming DWR from server - respond with DWA
                log.debug("event=diameter_exchange type=DWR_RECEIVED hopByHopId={} endToEndId={}",
                        message.getHeader().hopByHopId(), message.getHeader().endToEndId());
                watchdogScheduler.handleIncomingDwr(message);
            } else {
                // DWA response to our DWR
                log.debug("event=diameter_exchange type=DWA hopByHopId={} endToEndId={}",
                        message.getHeader().hopByHopId(), message.getHeader().endToEndId());
                watchdogScheduler.onDwaReceived();
            }
        }
    }
}
