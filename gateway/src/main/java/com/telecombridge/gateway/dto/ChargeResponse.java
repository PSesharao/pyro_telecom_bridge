package com.telecombridge.gateway.dto;

/**
 * Response DTO for the charge endpoint.
 *
 * @param sessionId          Diameter session identifier
 * @param resultCode         Diameter Result-Code (2001 = success)
 * @param grantedServiceUnit Granted service unit details (null when resultCode != 2001)
 */
public record ChargeResponse(
        String sessionId,
        long resultCode,
        GrantedServiceUnit grantedServiceUnit
) {
}
