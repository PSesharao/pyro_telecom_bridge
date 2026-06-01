package com.telecombridge.gateway.integration;

import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.dto.ChargeResponse;
import com.telecombridge.gateway.dto.ErrorResponse;
import com.telecombridge.simulator.DiameterSimulator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the full request flow through the gateway:
 * REST POST → CCR → CCA → JSON response.
 *
 * Starts a real DiameterSimulator on a random port and configures the
 * Spring Boot gateway to connect to it.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.4, 5.1, 5.2, 5.3
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayIntegrationTest {

    private static final int SIMULATOR_PORT = findFreePort();
    private static DiameterSimulator simulator;

    @LocalServerPort
    private int serverPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startSimulator() throws Exception {
        simulator = new DiameterSimulator(SIMULATOR_PORT, 10, 2);
        simulator.start();
        // Give the simulator a moment to bind
        Thread.sleep(200);
    }

    @AfterAll
    static void stopSimulator() {
        if (simulator != null) {
            simulator.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("diameter.host", () -> "localhost");
        registry.add("diameter.port", () -> SIMULATOR_PORT);
        registry.add("diameter.request-timeout-ms", () -> 3000);
        registry.add("diameter.watchdog-interval-ms", () -> 600000);
        registry.add("diameter.watchdog-timeout-ms", () -> 10000);
    }

    /**
     * Test full flow: valid charge request returns 200 with sessionId and resultCode=2001.
     * Verifies: REST POST → CCR → CCA → JSON response
     * Validates: Requirements 1.1, 1.2, 2.1, 2.4, 5.1, 5.2, 5.3
     */
    @Test
    void fullFlow_validRequest_returns200WithSessionIdAndResultCode() throws Exception {
        waitForGatewayReady();

        ChargeRequest request = new ChargeRequest("+441234567890", "12345", 1);

        ResponseEntity<ChargeResponse> response = restTemplate.postForEntity(
                "/api/v1/charge", request, ChargeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sessionId()).isNotBlank();
        assertThat(response.getBody().resultCode()).isEqualTo(2001L);
    }

    /**
     * Test validation error: missing MSISDN returns 400 with error details.
     * Validates: Requirements 1.3
     */
    @Test
    void validationError_missingMsisdn_returns400() {
        ChargeRequest request = new ChargeRequest(null, "12345", 1);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/charge", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Validation failed");
        assertThat(response.getBody().details()).isNotEmpty();
        assertThat(response.getBody().details().stream()
                .anyMatch(d -> d.toLowerCase().contains("msisdn"))).isTrue();
    }

    /**
     * Test validation error: invalid MSISDN format returns 400.
     * Validates: Requirements 1.4
     */
    @Test
    void validationError_invalidMsisdn_returns400() {
        ChargeRequest request = new ChargeRequest("not-a-number", "12345", 1);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/charge", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Validation failed");
        assertThat(response.getBody().details().stream()
                .anyMatch(d -> d.toLowerCase().contains("msisdn") || d.toLowerCase().contains("e.164")))
                .isTrue();
    }

    /**
     * Test validation error: invalid request type returns 400.
     * Validates: Requirements 1.5
     */
    @Test
    void validationError_invalidRequestType_returns400() {
        ChargeRequest request = new ChargeRequest("+441234567890", "12345", 99);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/charge", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Validation failed");
        assertThat(response.getBody().details().stream()
                .anyMatch(d -> d.toLowerCase().contains("requesttype") || d.toLowerCase().contains("request_type")
                        || d.toLowerCase().contains("request type") || d.contains("1") || d.contains("4")))
                .isTrue();
    }

    /**
     * Test all valid request types (1-4) succeed with 200.
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    void allRequestTypes_succeed() throws Exception {
        waitForGatewayReady();

        for (int requestType = 1; requestType <= 4; requestType++) {
            ChargeRequest request = new ChargeRequest("+441234567890", "12345", requestType);

            ResponseEntity<ChargeResponse> response = restTemplate.postForEntity(
                    "/api/v1/charge", request, ChargeResponse.class);

            assertThat(response.getStatusCode())
                    .as("Request type %d should return 200", requestType)
                    .isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().resultCode()).isEqualTo(2001L);
            assertThat(response.getBody().sessionId()).isNotBlank();
        }
    }

    /**
     * Waits for the gateway to establish a connection with the simulator
     * (CER/CEA exchange to complete). Polls the charge endpoint until it
     * returns a non-503 response.
     */
    private void waitForGatewayReady() throws InterruptedException {
        ChargeRequest probe = new ChargeRequest("+441234567890", "12345", 1);
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/charge", probe, String.class);
            if (response.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Gateway did not become ready within " + (maxAttempts * 500) + "ms");
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find a free port", e);
        }
    }
}
