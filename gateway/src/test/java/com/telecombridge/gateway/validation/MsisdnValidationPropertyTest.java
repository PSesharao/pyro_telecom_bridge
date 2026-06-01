package com.telecombridge.gateway.validation;

import com.telecombridge.gateway.dto.ChargeRequest;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for MSISDN Validation Correctness (Property 1).
 *
 * <p><b>Validates: Requirements 1.4</b></p>
 *
 * <p>For any string not matching {@code ^\+\d{8,15}$}, the validation logic SHALL reject it;
 * for any string that does match, the validation logic SHALL accept it.</p>
 */
@Tag("Feature: telecom-bridge, Property 1: MSISDN Validation Correctness")
class MsisdnValidationPropertyTest {

    private static final Pattern MSISDN_PATTERN = Pattern.compile("^\\+\\d{8,15}$");

    // ---- Generators ----

    /**
     * Generates valid E.164 MSISDN strings: '+' followed by 8 to 15 digits.
     */
    @Provide
    Arbitrary<String> validMsisdns() {
        return Arbitraries.integers().between(8, 15).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofLength(length)
                        .map(digits -> "+" + digits)
        );
    }

    /**
     * Generates invalid MSISDN strings that do NOT match the E.164 pattern.
     * Covers: no leading '+', too short, too long, non-digit characters, special chars.
     */
    @Provide
    Arbitrary<String> invalidMsisdns() {
        // Category 1: Missing leading '+' (digits only, valid length)
        Arbitrary<String> noPlus = Arbitraries.integers().between(8, 15).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofLength(length)
        );

        // Category 2: Too short ('+' followed by 1-7 digits)
        Arbitrary<String> tooShort = Arbitraries.integers().between(1, 7).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofLength(length)
                        .map(digits -> "+" + digits)
        );

        // Category 3: Too long ('+' followed by 16-25 digits)
        Arbitrary<String> tooLong = Arbitraries.integers().between(16, 25).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
                        .ofLength(length)
                        .map(digits -> "+" + digits)
        );

        // Category 4: Contains non-digit characters after '+'
        Arbitrary<String> nonDigits = Arbitraries.integers().between(8, 15).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                'a', 'b', 'c', 'x', 'y', 'z', 'A', 'B', 'C')
                        .ofLength(length)
                        .filter(s -> !s.matches("\\d+")) // Ensure at least one non-digit
                        .map(mixed -> "+" + mixed)
        );

        // Category 5: Special characters mixed in
        Arbitrary<String> specialChars = Arbitraries.integers().between(8, 15).flatMap(length ->
                Arbitraries.strings()
                        .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                '-', ' ', '.', '(', ')', '#', '*')
                        .ofLength(length)
                        .filter(s -> !s.matches("\\d+")) // Ensure at least one special char
                        .map(mixed -> "+" + mixed)
        );

        // Category 6: Empty string or just '+'
        Arbitrary<String> edgeCases = Arbitraries.of("", "+", "++12345678", "12345678+", " +12345678");

        return Arbitraries.oneOf(noPlus, tooShort, tooLong, nonDigits, specialChars, edgeCases);
    }

    // ---- Properties ----

    /**
     * Property 1a: Valid MSISDNs (matching pattern) are accepted by the validator.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 100)
    void validMsisdnIsAcceptedByValidator(@ForAll("validMsisdns") String msisdn) {
        // Verify our generator produces valid E.164 strings
        assertThat(MSISDN_PATTERN.matcher(msisdn).matches())
                .as("Generated MSISDN '%s' should match E.164 pattern", msisdn)
                .isTrue();

        // Create a request with valid MSISDN and other valid fields
        ChargeRequest request = new ChargeRequest(msisdn, "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);

        // No MSISDN-related errors should be present
        assertThat(errors.stream().anyMatch(e -> e.contains("msisdn")))
                .as("Valid MSISDN '%s' should not produce validation errors, but got: %s", msisdn, errors)
                .isFalse();
    }

    /**
     * Property 1b: Invalid MSISDNs (not matching pattern) are rejected by the validator.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 100)
    void invalidMsisdnIsRejectedByValidator(@ForAll("invalidMsisdns") String msisdn) {
        // Verify our generator produces strings that do NOT match E.164 pattern
        assertThat(MSISDN_PATTERN.matcher(msisdn).matches())
                .as("Generated invalid MSISDN '%s' should NOT match E.164 pattern", msisdn)
                .isFalse();

        // Skip blank/null strings as those are handled by @NotBlank Bean Validation,
        // not the custom validator
        if (msisdn == null || msisdn.isBlank()) {
            return;
        }

        // Create a request with invalid MSISDN and other valid fields
        ChargeRequest request = new ChargeRequest(msisdn, "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);

        // Should contain an MSISDN validation error
        assertThat(errors.stream().anyMatch(e -> e.contains("msisdn")))
                .as("Invalid MSISDN '%s' should produce a validation error, but got no msisdn error. Errors: %s",
                        msisdn, errors)
                .isTrue();
    }
}
