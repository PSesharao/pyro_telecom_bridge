package com.telecombridge.gateway.diameter;

import com.telecombridge.gateway.dto.GrantedServiceUnit;

/**
 * Parsed CCA (Credit Control Answer) data extracted from a Diameter message.
 *
 * @param sessionId           the Session-Id from the CCA
 * @param resultCode          the Result-Code AVP value (e.g., 2001 for success)
 * @param ccRequestType       the CC-Request-Type echoed from the CCR
 * @param ccRequestNumber     the CC-Request-Number echoed from the CCR
 * @param grantedServiceUnit  the granted service unit (nullable, absent for non-success)
 */
public record CcaData(
        String sessionId,
        long resultCode,
        int ccRequestType,
        int ccRequestNumber,
        GrantedServiceUnit grantedServiceUnit
) {
}
