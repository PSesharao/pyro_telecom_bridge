package com.telecombridge.gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Error response DTO returned for validation failures and other errors.
 *
 * @param error     Error category description
 * @param details   List of field-specific error messages
 * @param timestamp Time when the error occurred
 */
public record ErrorResponse(
        String error,
        List<String> details,
        Instant timestamp
) {
}
