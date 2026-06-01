package com.telecombridge.gateway.service;

import com.telecombridge.codec.Avp;
import com.telecombridge.codec.AvpCodes;
import com.telecombridge.codec.CommandCodes;
import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterHeader;
import com.telecombridge.codec.DiameterMessage;
import com.telecombridge.gateway.config.DiameterProperties;
import com.telecombridge.gateway.diameter.CcaData;
import com.telecombridge.gateway.diameter.DiameterClient;
import com.telecombridge.gateway.diameter.IdGenerator;
import com.telecombridge.gateway.diameter.RequestCorrelator;
import com.telecombridge.gateway.diameter.SessionIdGenerator;
import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.dto.ChargeResponse;
import com.telecombridge.gateway.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the construction of Diameter CCR messages from REST charge requests,
 * manages correlation of responses, and maps CCA data back to REST responses.
 */
@Service
public class ChargeService {

    private static final Logger log = LoggerFactory.getLogger(ChargeService.class);

    private static final int CREDIT_CONTROL_APP_ID = 4;
    private static final byte CCR_FLAGS = (byte) 0xC0; // Request (0x80) + Proxiable (0x40)
    private static final byte AVP_MANDATORY = Avp.FLAG_MANDATORY;
    private static final int SUBSCRIPTION_ID_TYPE_END_USER_E164 = 0;
    private static final long DIAMETER_SUCCESS = 2001L;

    private final DiameterClient diameterClient;
    private final RequestCorrelator correlator;
    private final IdGenerator idGenerator;
    private final SessionIdGenerator sessionIdGenerator;
    private final DiameterProperties properties;
    private final MetricsCollector metricsCollector;

    public ChargeService(DiameterClient diameterClient,
                         RequestCorrelator correlator,
                         IdGenerator idGenerator,
                         SessionIdGenerator sessionIdGenerator,
                         DiameterProperties properties,
                         MetricsCollector metricsCollector) {
        this.diameterClient = diameterClient;
        this.correlator = correlator;
        this.idGenerator = idGenerator;
        this.sessionIdGenerator = sessionIdGenerator;
        this.properties = properties;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Processes a charge request by constructing a CCR, sending it to the Diameter server,
     * and mapping the CCA response back to a ChargeResponse.
     *
     * @param request the charge request from the REST endpoint
     * @return a CompletableFuture that completes with the charge response
     * @throws DiameterConnectionException if the Diameter client is not ready
     */
    public CompletableFuture<ChargeResponse> processCharge(ChargeRequest request) {
        // Step 1: Check if DiameterClient is ready
        if (!diameterClient.isReady()) {
            throw new DiameterConnectionException("Diameter connection not established");
        }

        // Record start time for latency tracking
        long startTimeMs = System.currentTimeMillis();

        // Step 2: Generate IDs
        String sessionId = sessionIdGenerator.generate();
        long hopByHopId = idGenerator.nextHopByHopId();
        long endToEndId = idGenerator.nextEndToEndId();

        // Step 3: Build CCR DiameterMessage
        DiameterMessage ccr = buildCcr(sessionId, hopByHopId, endToEndId, request);

        // Step 4: Create CompletableFuture for the response
        CompletableFuture<CcaData> ccaFuture = new CompletableFuture<>();

        // Step 5: Register in correlator with deadline
        Instant deadline = Instant.now().plusMillis(properties.getRequestTimeoutMs());
        correlator.register(hopByHopId, ccaFuture, deadline, sessionId);

        // Step 6: Send CCR via DiameterClient
        CompletableFuture<Void> sendFuture = diameterClient.send(ccr);

        // Step 7: If send fails, complete exceptionally and remove from correlator
        sendFuture.whenComplete((result, error) -> {
            if (error != null) {
                log.error("event=send_failure category=connection_error sessionId={} hopByHopId={} remoteHost={} cause={}",
                        sessionId, hopByHopId, properties.getHost() + ":" + properties.getPort(), error.getMessage());
                correlator.completeExceptionally(hopByHopId, error);
            }
        });

        // Step 8: Map CcaData → ChargeResponse and record latency
        return ccaFuture.thenApply(ccaData -> mapToChargeResponse(ccaData))
                .whenComplete((response, error) -> {
                    long latencyMs = System.currentTimeMillis() - startTimeMs;
                    metricsCollector.recordLatency(latencyMs);
                });
    }

    /**
     * Builds a CCR DiameterMessage with all mandatory AVPs.
     */
    private DiameterMessage buildCcr(String sessionId, long hopByHopId, long endToEndId,
                                      ChargeRequest request) {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // Message length will be calculated during encoding
                CCR_FLAGS,
                CommandCodes.CREDIT_CONTROL,
                CREDIT_CONTROL_APP_ID,
                hopByHopId,
                endToEndId
        );

        DiameterMessage ccr = new DiameterMessage(header);

        // Session-Id (263): UTF8String
        ccr.addAvp(createUtf8Avp(AvpCodes.SESSION_ID, sessionId));

        // Auth-Application-Id (258): Unsigned32 = 4
        ccr.addAvp(createUnsigned32Avp(AvpCodes.AUTH_APPLICATION_ID, CREDIT_CONTROL_APP_ID));

        // Origin-Host (264): UTF8String
        ccr.addAvp(createUtf8Avp(AvpCodes.ORIGIN_HOST, properties.getOriginHost()));

        // Origin-Realm (296): UTF8String
        ccr.addAvp(createUtf8Avp(AvpCodes.ORIGIN_REALM, properties.getOriginRealm()));

        // Destination-Realm (283): UTF8String
        ccr.addAvp(createUtf8Avp(AvpCodes.DESTINATION_REALM, properties.getDestinationRealm()));

        // CC-Request-Type (416): Unsigned32
        ccr.addAvp(createUnsigned32Avp(AvpCodes.CC_REQUEST_TYPE, request.requestType()));

        // CC-Request-Number (415): Unsigned32 = 0
        ccr.addAvp(createUnsigned32Avp(AvpCodes.CC_REQUEST_NUMBER, 0));

        // Subscription-Id (443): Grouped AVP
        ccr.addAvp(createSubscriptionIdAvp(request.msisdn()));

        return ccr;
    }

    /**
     * Creates a UTF8String AVP with the Mandatory flag set.
     */
    private Avp createUtf8Avp(int code, String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        return new Avp(code, AVP_MANDATORY, data);
    }

    /**
     * Creates an Unsigned32 AVP with the Mandatory flag set.
     */
    private Avp createUnsigned32Avp(int code, long value) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        return new Avp(code, AVP_MANDATORY, data);
    }

    /**
     * Creates a Subscription-Id grouped AVP containing Subscription-Id-Type and Subscription-Id-Data.
     */
    private Avp createSubscriptionIdAvp(String msisdn) {
        // Build nested AVPs as raw bytes (grouped AVP data is the concatenation of encoded nested AVPs)
        Avp typeAvp = createUnsigned32Avp(AvpCodes.SUBSCRIPTION_ID_TYPE, SUBSCRIPTION_ID_TYPE_END_USER_E164);
        Avp dataAvp = createUtf8Avp(AvpCodes.SUBSCRIPTION_ID_DATA, msisdn);

        // Encode nested AVPs into grouped data
        byte[] groupedData = encodeNestedAvps(typeAvp, dataAvp);

        return new Avp(AvpCodes.SUBSCRIPTION_ID, AVP_MANDATORY, groupedData);
    }

    /**
     * Encodes nested AVPs into a byte array for use as grouped AVP data.
     * Each nested AVP is encoded with its header, data, and padding.
     */
    private byte[] encodeNestedAvps(Avp... avps) {
        // Calculate total size needed
        int totalSize = 0;
        for (Avp avp : avps) {
            int headerSize = avp.isVendorSpecific() ? Avp.HEADER_SIZE_VENDOR : Avp.HEADER_SIZE;
            int avpLength = headerSize + avp.getData().length;
            int paddedLength = avpLength + ((4 - (avpLength % 4)) % 4);
            totalSize += paddedLength;
        }

        byte[] result = new byte[totalSize];
        int offset = 0;

        for (Avp avp : avps) {
            byte[] data = avp.getData();
            int headerSize = avp.isVendorSpecific() ? Avp.HEADER_SIZE_VENDOR : Avp.HEADER_SIZE;
            int avpLength = headerSize + data.length;
            int padding = (4 - (avpLength % 4)) % 4;

            // AVP Code (4 bytes, big-endian)
            result[offset] = (byte) ((avp.getCode() >> 24) & 0xFF);
            result[offset + 1] = (byte) ((avp.getCode() >> 16) & 0xFF);
            result[offset + 2] = (byte) ((avp.getCode() >> 8) & 0xFF);
            result[offset + 3] = (byte) (avp.getCode() & 0xFF);

            // Flags (1 byte)
            result[offset + 4] = avp.getFlags();

            // Length (3 bytes, big-endian)
            result[offset + 5] = (byte) ((avpLength >> 16) & 0xFF);
            result[offset + 6] = (byte) ((avpLength >> 8) & 0xFF);
            result[offset + 7] = (byte) (avpLength & 0xFF);

            // Vendor-ID if vendor-specific
            int dataOffset = offset + 8;
            if (avp.isVendorSpecific()) {
                result[offset + 8] = (byte) ((avp.getVendorId() >> 24) & 0xFF);
                result[offset + 9] = (byte) ((avp.getVendorId() >> 16) & 0xFF);
                result[offset + 10] = (byte) ((avp.getVendorId() >> 8) & 0xFF);
                result[offset + 11] = (byte) (avp.getVendorId() & 0xFF);
                dataOffset = offset + 12;
            }

            // Data
            System.arraycopy(data, 0, result, dataOffset, data.length);

            // Padding is already zero (default byte array value)
            offset += avpLength + padding;
        }

        return result;
    }

    /**
     * Maps CCA data to a ChargeResponse.
     * If resultCode is not 2001, grantedServiceUnit is null.
     */
    private ChargeResponse mapToChargeResponse(CcaData ccaData) {
        if (ccaData.resultCode() != DIAMETER_SUCCESS) {
            return new ChargeResponse(ccaData.sessionId(), ccaData.resultCode(), null);
        }
        return new ChargeResponse(ccaData.sessionId(), ccaData.resultCode(), ccaData.grantedServiceUnit());
    }
}
