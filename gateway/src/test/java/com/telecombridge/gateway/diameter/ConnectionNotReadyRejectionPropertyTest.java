package com.telecombridge.gateway.diameter;

import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterMessage;
import com.telecombridge.gateway.config.DiameterProperties;
import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.metrics.MetricsCollector;
import com.telecombridge.gateway.service.ChargeService;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Property-based test for connection not ready rejection (Property 14).
 *
 * <p><b>Validates: Requirements 5.6, 7.2</b></p>
 *
 * <p>Property 14: For any charge request submitted while the Diameter client connection
 * is not in the READY state, the Gateway SHALL reject the request with a
 * DiameterConnectionException without attempting to send a CCR or modifying the Correlation Map.</p>
 */
@Tag("Feature: telecom-bridge, Property 14: Connection Not Ready Rejects All Requests")
class ConnectionNotReadyRejectionPropertyTest {

    private final DiameterClient diameterClient;
    private final RequestCorrelator correlator;
    private final ChargeService chargeService;

    ConnectionNotReadyRejectionPropertyTest() {
        this.diameterClient = mock(DiameterClient.class);
        this.correlator = new RequestCorrelator();

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

        // Configure DiameterClient to NOT be ready
        when(diameterClient.isReady()).thenReturn(false);
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
     * Property 14: Connection Not Ready Rejects All Requests
     *
     * For any charge request while not READY, Gateway rejects with DiameterConnectionException
     * without sending CCR or modifying Correlation Map.
     *
     * <p><b>Validates: Requirements 5.6, 7.2</b></p>
     */
    @Property(tries = 100)
    void connectionNotReadyRejectsAllRequestsWithoutSendingCcrOrModifyingCorrelationMap(
            @ForAll("validMsisdns") String msisdn,
            @ForAll("validServiceIdentifiers") String serviceIdentifier,
            @ForAll("validRequestTypes") Integer requestType) {

        ChargeRequest request = new ChargeRequest(msisdn, serviceIdentifier, requestType);

        // Verify that processCharge throws DiameterConnectionException
        assertThatThrownBy(() -> chargeService.processCharge(request))
                .isInstanceOf(DiameterConnectionException.class)
                .hasMessageContaining("Diameter connection not established");

        // Verify no CCR was sent via DiameterClient
        verify(diameterClient, never()).send(any(DiameterMessage.class));

        // Verify the Correlation Map was not modified (no entries added)
        assertThat(correlator.pendingCount())
                .as("Correlation Map should have no entries when connection is not ready")
                .isEqualTo(0);
    }
}
