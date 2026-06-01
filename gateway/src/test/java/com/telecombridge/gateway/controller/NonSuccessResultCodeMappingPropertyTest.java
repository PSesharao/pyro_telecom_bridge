package com.telecombridge.gateway.controller;

import com.telecombridge.gateway.diameter.CcaData;
import com.telecombridge.gateway.dto.ChargeResponse;
import com.telecombridge.gateway.dto.GrantedServiceUnit;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for non-success result code mapping.
 *
 * <p><b>Validates: Requirements 1.6</b></p>
 *
 * <p>Property 3: For any CCA response with a Result_Code value other than 2001,
 * the mapped HTTP response SHALL contain the Session_ID and Result_Code but
 * SHALL NOT contain a Granted-Service-Unit object.</p>
 */
@Tag("Feature: telecom-bridge, Property 3: Non-Success Result Code Mapping")
class NonSuccessResultCodeMappingPropertyTest {

    private static final long DIAMETER_SUCCESS = 2001L;

    /**
     * Provides random unsigned 32-bit integers (0 to 0xFFFFFFFFL) excluding 2001.
     */
    @Provide
    Arbitrary<Long> nonSuccessResultCodes() {
        return Arbitraries.longs()
                .between(0L, 0xFFFFFFFFL)
                .filter(code -> code != DIAMETER_SUCCESS);
    }

    /**
     * Provides random session IDs (non-empty strings).
     */
    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(64)
                .map(s -> "CTOPUP;" + s + ";1");
    }

    /**
     * Provides random GrantedServiceUnit instances (non-null) to ensure
     * the mapping logic actively nullifies them for non-success codes.
     */
    @Provide
    Arbitrary<GrantedServiceUnit> grantedServiceUnits() {
        Arbitrary<Long> optionalLong = Arbitraries.longs().between(0L, 100000L);
        return Combinators.combine(optionalLong, optionalLong, optionalLong)
                .as(GrantedServiceUnit::new);
    }

    /**
     * Property 3: Non-Success Result Code Mapping
     *
     * For any CCA with Result_Code ≠ 2001, the mapped ChargeResponse contains
     * the Session_ID and Result_Code but grantedServiceUnit is null.
     *
     * <p><b>Validates: Requirements 1.6</b></p>
     */
    @Property(tries = 100)
    void nonSuccessResultCodeMapsToResponseWithoutGrantedServiceUnit(
            @ForAll("nonSuccessResultCodes") Long resultCode,
            @ForAll("sessionIds") String sessionId,
            @ForAll("grantedServiceUnits") GrantedServiceUnit gsu) throws Exception {

        // Create CcaData with a non-success result code but WITH a GrantedServiceUnit
        // to verify the mapping logic actively removes it
        CcaData ccaData = new CcaData(
                sessionId,
                resultCode,
                1, // ccRequestType (INITIAL)
                0, // ccRequestNumber
                gsu // non-null GSU to prove mapping nullifies it
        );

        // Use reflection to invoke the private mapToChargeResponse method
        ChargeResponse response = invokeMapToChargeResponse(ccaData);

        // Assert: Session_ID is present and matches
        assertThat(response.sessionId())
                .as("Response should contain the Session_ID from the CCA")
                .isEqualTo(sessionId);

        // Assert: Result_Code is present and matches
        assertThat(response.resultCode())
                .as("Response should contain the Result_Code from the CCA")
                .isEqualTo(resultCode);

        // Assert: Granted-Service-Unit is null for non-success result codes
        assertThat(response.grantedServiceUnit())
                .as("Response should NOT contain Granted-Service-Unit when Result_Code ≠ 2001")
                .isNull();
    }

    /**
     * Invokes the private mapToChargeResponse method on ChargeService via reflection.
     * This tests the actual mapping logic without needing to wire up the full service.
     */
    private ChargeResponse invokeMapToChargeResponse(CcaData ccaData) throws Exception {
        Class<?> chargeServiceClass = Class.forName("com.telecombridge.gateway.service.ChargeService");
        Method method = chargeServiceClass.getDeclaredMethod("mapToChargeResponse", CcaData.class);
        method.setAccessible(true);

        // Create an instance using the constructor with null dependencies
        // (mapToChargeResponse doesn't use any dependencies)
        Object chargeService = chargeServiceClass.getDeclaredConstructors()[0].newInstance(
                null, null, null, null, null, null);

        return (ChargeResponse) method.invoke(chargeService, ccaData);
    }
}
