package com.telecombridge.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecombridge.gateway.exception.GlobalExceptionHandler;
import com.telecombridge.gateway.service.ChargeService;
import net.jqwik.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for missing fields identification.
 *
 * <p><b>Validates: Requirements 1.3</b></p>
 *
 * <p>Property 2: For any subset of required fields {msisdn, serviceIdentifier, requestType}
 * that is omitted from a charge request, the error response identifies exactly those
 * missing fields by name, with no false positives and no false negatives.</p>
 */
@Tag("Feature: telecom-bridge, Property 2: Missing Fields Identification")
class MissingFieldsIdentificationPropertyTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    /**
     * All 3 required fields.
     */
    private static final List<String> ALL_REQUIRED_FIELDS = List.of("msisdn", "serviceIdentifier", "requestType");

    /**
     * Valid values for each field when present.
     */
    private static final Map<String, Object> VALID_VALUES = Map.of(
            "msisdn", "+12345678901",
            "serviceIdentifier", "100",
            "requestType", 1
    );

    MissingFieldsIdentificationPropertyTest() {
        ChargeService chargeService = mock(ChargeService.class);
        ChargeController controller = new ChargeController(chargeService);
        this.objectMapper = new ObjectMapper();
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Provides all 7 non-empty subsets of the 3 required fields.
     * 2^3 - 1 = 7 subsets (excluding the empty set).
     */
    @Provide
    Arbitrary<Set<String>> missingFieldSubsets() {
        List<Set<String>> allSubsets = new ArrayList<>();
        List<String> fields = ALL_REQUIRED_FIELDS;

        // Generate all 2^3 - 1 = 7 non-empty subsets
        for (int mask = 1; mask < (1 << fields.size()); mask++) {
            Set<String> subset = new LinkedHashSet<>();
            for (int i = 0; i < fields.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(fields.get(i));
                }
            }
            allSubsets.add(subset);
        }

        return Arbitraries.of(allSubsets);
    }

    /**
     * Property 2: Missing Fields Identification
     *
     * For any subset of required fields omitted, the error response identifies
     * exactly those fields (no false positives/negatives).
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 70)
    void missingFieldsAreExactlyIdentifiedInErrorResponse(
            @ForAll("missingFieldSubsets") Set<String> missingFields) throws Exception {

        // Build JSON request body with only the fields NOT in missingFields
        Map<String, Object> requestBody = new HashMap<>();
        for (String field : ALL_REQUIRED_FIELDS) {
            if (!missingFields.contains(field)) {
                requestBody.put(field, VALID_VALUES.get(field));
            }
        }

        String json = objectMapper.writeValueAsString(requestBody);

        // Perform the request - should return 400 since at least one field is missing
        MvcResult result = mockMvc.perform(post("/api/v1/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Parse the error response
        String responseBody = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> errorResponse = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

        assertThat(errorResponse.get("error")).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) errorResponse.get("details");
        assertThat(details).isNotNull();

        // Extract field names from error details (format: "fieldName: message")
        Set<String> reportedFields = details.stream()
                .map(detail -> detail.split(":")[0].trim())
                .collect(Collectors.toSet());

        // Property assertion: reported fields must be EXACTLY the missing fields
        // No false positives (no extra fields reported)
        // No false negatives (no missing fields unreported)
        assertThat(reportedFields)
                .as("Error response should identify exactly the missing fields %s but reported %s",
                        missingFields, reportedFields)
                .isEqualTo(missingFields);
    }
}
