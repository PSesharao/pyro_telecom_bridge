package com.telecombridge.gateway.dto;

/**
 * Granted service unit details from a CCA response.
 *
 * @param ccTime                 Credit control time in seconds (nullable)
 * @param ccTotalOctets          Credit control total octets in bytes (nullable)
 * @param ccServiceSpecificUnits Credit control service-specific units (nullable)
 */
public record GrantedServiceUnit(
        Long ccTime,
        Long ccTotalOctets,
        Long ccServiceSpecificUnits
) {
}
