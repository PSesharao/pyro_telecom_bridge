package com.telecombridge.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for the charge endpoint.
 *
 * @param msisdn            Subscriber phone number in E.164 format (+[8-15 digits])
 * @param serviceIdentifier Numeric string identifying the service (1-32 digits)
 * @param requestType       Credit control request type (1=INITIAL, 2=UPDATE, 3=TERMINATION, 4=EVENT)
 */
public record ChargeRequest(
        @NotBlank(message = "msisdn is required")
        String msisdn,

        @NotBlank(message = "serviceIdentifier is required")
        String serviceIdentifier,

        @NotNull(message = "requestType is required")
        Integer requestType
) {
}
