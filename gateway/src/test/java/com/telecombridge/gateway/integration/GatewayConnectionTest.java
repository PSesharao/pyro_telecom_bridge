package com.telecombridge.gateway.integration;

import com.telecombridge.codec.DiameterConnectionException;
import com.telecombridge.codec.DiameterTimeoutException;
import com.telecombridge.gateway.controller.ChargeController;
import com.telecombridge.gateway.diameter.CcaData;
import com.telecombridge.gateway.diameter.DiameterClient;
import com.telecombridge.gateway.dto.ChargeRequest;
import com.telecombridge.gateway.dto.ErrorResponse;
import com.telecombridge.gateway.exception.GlobalExceptionHandler;
import com.telecombridge.gateway.service.ChargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for connection-related error scenarios:
 * - Request timeout returning HTTP 504
 * - Connection unavailable returning HTTP 503
 *
 * Uses MockMvc with a mocked ChargeService to simulate error conditions
 * that are difficult to reproduce with a real simulator.
 *
 * Validates: Requirements 5.4, 5.6, 6.1, 6.3, 7.1, 7.2
 */
@WebMvcTest(ChargeController.class)
class GatewayConnectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChargeService chargeService;

    private static final String VALID_REQUEST_JSON = """
            {
                "msisdn": "+441234567890",
                "serviceIdentifier": "12345",
                "requestType": 1
            }
            """;

    /**
     * Test request timeout returns HTTP 504 Gateway Timeout.
     * Simulates a scenario where the Diameter server does not respond within the timeout.
     * Validates: Requirements 7.1
     */
    @Test
    void requestTimeout_returns504() throws Exception {
        CompletableFuture<com.telecombridge.gateway.dto.ChargeResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new DiameterTimeoutException("Request timed out after 5000ms for session: test-session"));

        when(chargeService.processCharge(any(ChargeRequest.class))).thenReturn(future);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("Gateway Timeout"))
                .andExpect(jsonPath("$.details[0]").value(containsString("timed out")));
    }

    /**
     * Test connection unavailable returns HTTP 503 Service Unavailable.
     * Simulates a scenario where the Diameter client is not connected.
     * Validates: Requirements 5.6, 7.2
     */
    @Test
    void connectionUnavailable_returns503() throws Exception {
        when(chargeService.processCharge(any(ChargeRequest.class)))
                .thenThrow(new DiameterConnectionException("Diameter connection not established"));

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.details[0]").value(containsString("Diameter connection not established")));
    }

    /**
     * Test validation error: missing required fields returns HTTP 400.
     * Validates: Requirements 1.3
     */
    @Test
    void missingRequiredFields_returns400() throws Exception {
        String emptyRequest = """
                {
                    "serviceIdentifier": "12345",
                    "requestType": 1
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }
}
