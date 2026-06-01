package com.telecombridge.gateway.controller;

import com.telecombridge.gateway.dto.ChargeResponse;
import com.telecombridge.gateway.service.ChargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChargeController.class)
class ChargeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChargeService chargeService;

    @Test
    void validRequest_returns200() throws Exception {
        ChargeResponse mockResponse = new ChargeResponse("test-session-id", 2001L, null);
        when(chargeService.processCharge(any())).thenReturn(CompletableFuture.completedFuture(mockResponse));

        String json = """
                {
                    "msisdn": "+12345678901",
                    "serviceIdentifier": "100",
                    "requestType": 1
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.resultCode").value(2001));
    }

    @Test
    void missingMsisdn_returns400() throws Exception {
        String json = """
                {
                    "serviceIdentifier": "100",
                    "requestType": 1
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingRequestType_returns400() throws Exception {
        String json = """
                {
                    "msisdn": "+12345678901",
                    "serviceIdentifier": "100"
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("requestType: requestType is required"));
    }

    @Test
    void missingAllFields_returns400WithAllErrors() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void invalidMsisdn_returns400() throws Exception {
        String json = """
                {
                    "msisdn": "12345",
                    "serviceIdentifier": "100",
                    "requestType": 1
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(
                        "msisdn: must be in E.164 format (leading '+' followed by 8 to 15 digits)"));
    }

    @Test
    void invalidRequestType_returns400() throws Exception {
        String json = """
                {
                    "msisdn": "+12345678901",
                    "serviceIdentifier": "100",
                    "requestType": 5
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(
                        "requestType: must be one of 1 (INITIAL), 2 (UPDATE), 3 (TERMINATION), or 4 (EVENT)"));
    }

    @Test
    void invalidServiceIdentifier_returns400() throws Exception {
        String json = """
                {
                    "msisdn": "+12345678901",
                    "serviceIdentifier": "abc",
                    "requestType": 1
                }
                """;

        mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(
                        "serviceIdentifier: must be a numeric string of 1 to 32 digits"));
    }

    @Test
    void allRequestTypes_valid() throws Exception {
        ChargeResponse mockResponse = new ChargeResponse("test-session-id", 2001L, null);
        when(chargeService.processCharge(any())).thenReturn(CompletableFuture.completedFuture(mockResponse));

        for (int type : new int[]{1, 2, 3, 4}) {
            String json = """
                    {
                        "msisdn": "+12345678901",
                        "serviceIdentifier": "100",
                        "requestType": %d
                    }
                    """.formatted(type);

            MvcResult mvcResult = mockMvc.perform(post("/api/v1/charge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk());
        }
    }
}
