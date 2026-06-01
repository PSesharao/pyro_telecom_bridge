package com.telecombridge.gateway.service;

import com.telecombridge.codec.Avp;
import com.telecombridge.codec.AvpCodes;
import com.telecombridge.codec.CommandCodes;
import com.telecombridge.codec.DiameterMessage;
import com.telecombridge.gateway.config.DiameterProperties;
import com.telecombridge.gateway.diameter.*;
import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.metrics.MetricsCollector;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for CCR Construction Invariants (Property 4).
 *
 * <p><b>Validates: Requirements 2.1, 3.4, 3.5, 3.6</b></p>
 *
 * <p>Property 4: For any valid ChargeRequest, the constructed CCR message SHALL have
 * Command Code 272, Application-ID 4, Command Flags 0xC0 (Request + Proxiable),
 * and SHALL contain all mandatory AVPs (Session-Id, Auth-Application-Id=4, Origin-Host,
 * Origin-Realm, Destination-Realm, CC-Request-Type, CC-Request-Number, Subscription-Id)
 * with the Mandatory bit (0x40) set in their AVP flags.</p>
 */
@Tag("Feature: telecom-bridge, Property 4: CCR Construction Invariants")
class CcrConstructionInvariantsPropertyTest {

    private static final byte CCR_FLAGS = (byte) 0xC0; // Request (0x80) + Proxiable (0x40)
    private static final int CREDIT_CONTROL_APP_ID = 4;
    private static final byte AVP_MANDATORY_BIT = Avp.FLAG_MANDATORY; // 0x40

    private final DiameterClient diameterClient;
    private final ChargeService chargeService;
    private final ArgumentCaptor<DiameterMessage> messageCaptor;

    CcrConstructionInvariantsPropertyTest() {
        this.diameterClient = mock(DiameterClient.class);
        RequestCorrelator correlator = new RequestCorrelator();
        IdGenerator idGenerator = new IdGenerator();
        SessionIdGenerator sessionIdGenerator = new SessionIdGenerator("CTOPUP");
        DiameterProperties properties = new DiameterProperties();
        properties.setHost("localhost");
        properties.setPort(3868);
        properties.setOriginHost("CTOPUP");
        properties.setOriginRealm("ctop.com");
        properties.setDestinationRealm("BSNL.NET");
        properties.setRequestTimeoutMs(5000);
        properties.setWatchdogIntervalMs(30000);
        properties.setWatchdogTimeoutMs(10000);
        properties.setThreadPoolSize(4);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);

        this.chargeService = new ChargeService(
                diameterClient, correlator, idGenerator, sessionIdGenerator, properties, metricsCollector);

        // Configure DiameterClient to be ready and capture sent messages
        when(diameterClient.isReady()).thenReturn(true);
        this.messageCaptor = ArgumentCaptor.forClass(DiameterMessage.class);
        when(diameterClient.send(any(DiameterMessage.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    /**
     * Provides random valid MSISDN strings in E.164 format.
     */
    @Provide
    Arbitrary<String> validMsisdns() {
        return Arbitraries.integers().between(8, 15)
                .flatMap(length -> Arbitraries.strings()
                        .withCharRange('0', '9')
                        .ofLength(length)
                        .map(digits -> "+" + digits));
    }

    /**
     * Provides random valid service identifiers (numeric strings, 1-32 digits).
     */
    @Provide
    Arbitrary<String> validServiceIdentifiers() {
        return Arbitraries.integers().between(1, 32)
                .flatMap(length -> Arbitraries.strings()
                        .withCharRange('0', '9')
                        .ofLength(length));
    }

    /**
     * Provides random valid request types (1-4).
     */
    @Provide
    Arbitrary<Integer> validRequestTypes() {
        return Arbitraries.integers().between(1, 4);
    }

    /**
     * Property 4: CCR Construction Invariants
     *
     * For any valid ChargeRequest, CCR has Command Code 272, App-ID 4, flags 0xC0,
     * and all mandatory AVPs with Mandatory bit set.
     *
     * <p><b>Validates: Requirements 2.1, 3.4, 3.5, 3.6</b></p>
     */
    @Property(tries = 100)
    void ccrHasCorrectHeaderAndAllMandatoryAvpsWithMandatoryBitSet(
            @ForAll("validMsisdns") String msisdn,
            @ForAll("validServiceIdentifiers") String serviceIdentifier,
            @ForAll("validRequestTypes") Integer requestType) {

        // Reset mock to capture fresh message
        reset(diameterClient);
        when(diameterClient.isReady()).thenReturn(true);
        when(diameterClient.send(messageCaptor.capture())).thenReturn(CompletableFuture.completedFuture(null));

        ChargeRequest request = new ChargeRequest(msisdn, serviceIdentifier, requestType);

        // Trigger CCR construction by calling processCharge
        chargeService.processCharge(request);

        // Capture the CCR message sent to DiameterClient
        DiameterMessage ccr = messageCaptor.getValue();
        assertThat(ccr).as("CCR message should have been sent").isNotNull();

        // Verify header: Command Code = 272
        assertThat(ccr.getHeader().commandCode())
                .as("CCR Command Code must be 272 (Credit-Control)")
                .isEqualTo(CommandCodes.CREDIT_CONTROL);

        // Verify header: Application-ID = 4
        assertThat(ccr.getHeader().applicationId())
                .as("CCR Application-ID must be 4 (Diameter Credit-Control)")
                .isEqualTo(CREDIT_CONTROL_APP_ID);

        // Verify header: Command Flags = 0xC0 (Request + Proxiable)
        assertThat(ccr.getHeader().commandFlags())
                .as("CCR Command Flags must be 0xC0 (Request 0x80 + Proxiable 0x40)")
                .isEqualTo(CCR_FLAGS);

        // Verify mandatory AVPs are present
        List<Avp> avps = ccr.getAvps();

        // Session-Id (263) - must be present with Mandatory bit
        Optional<Avp> sessionId = ccr.findAvp(AvpCodes.SESSION_ID);
        assertThat(sessionId).as("Session-Id AVP (263) must be present").isPresent();
        assertMandatoryBitSet(sessionId.get(), "Session-Id");

        // Auth-Application-Id (258) - must be present with value 4 and Mandatory bit
        Optional<Avp> authAppId = ccr.findAvp(AvpCodes.AUTH_APPLICATION_ID);
        assertThat(authAppId).as("Auth-Application-Id AVP (258) must be present").isPresent();
        assertMandatoryBitSet(authAppId.get(), "Auth-Application-Id");
        assertThat(authAppId.get().asUnsigned32())
                .as("Auth-Application-Id must be 4")
                .isEqualTo(4L);

        // Origin-Host (264) - must be present with Mandatory bit
        Optional<Avp> originHost = ccr.findAvp(AvpCodes.ORIGIN_HOST);
        assertThat(originHost).as("Origin-Host AVP (264) must be present").isPresent();
        assertMandatoryBitSet(originHost.get(), "Origin-Host");

        // Origin-Realm (296) - must be present with Mandatory bit
        Optional<Avp> originRealm = ccr.findAvp(AvpCodes.ORIGIN_REALM);
        assertThat(originRealm).as("Origin-Realm AVP (296) must be present").isPresent();
        assertMandatoryBitSet(originRealm.get(), "Origin-Realm");

        // Destination-Realm (283) - must be present with Mandatory bit
        Optional<Avp> destRealm = ccr.findAvp(AvpCodes.DESTINATION_REALM);
        assertThat(destRealm).as("Destination-Realm AVP (283) must be present").isPresent();
        assertMandatoryBitSet(destRealm.get(), "Destination-Realm");

        // CC-Request-Type (416) - must be present with Mandatory bit
        Optional<Avp> ccRequestType = ccr.findAvp(AvpCodes.CC_REQUEST_TYPE);
        assertThat(ccRequestType).as("CC-Request-Type AVP (416) must be present").isPresent();
        assertMandatoryBitSet(ccRequestType.get(), "CC-Request-Type");

        // CC-Request-Number (415) - must be present with Mandatory bit
        Optional<Avp> ccRequestNumber = ccr.findAvp(AvpCodes.CC_REQUEST_NUMBER);
        assertThat(ccRequestNumber).as("CC-Request-Number AVP (415) must be present").isPresent();
        assertMandatoryBitSet(ccRequestNumber.get(), "CC-Request-Number");

        // Subscription-Id (443) - must be present with Mandatory bit
        Optional<Avp> subscriptionId = ccr.findAvp(AvpCodes.SUBSCRIPTION_ID);
        assertThat(subscriptionId).as("Subscription-Id AVP (443) must be present").isPresent();
        assertMandatoryBitSet(subscriptionId.get(), "Subscription-Id");
    }

    /**
     * Asserts that the Mandatory bit (0x40) is set in the AVP flags.
     */
    private void assertMandatoryBitSet(Avp avp, String avpName) {
        assertThat((avp.getFlags() & AVP_MANDATORY_BIT) != 0)
                .as(avpName + " AVP must have Mandatory bit (0x40) set, but flags=0x%02X", avp.getFlags())
                .isTrue();
    }
}
