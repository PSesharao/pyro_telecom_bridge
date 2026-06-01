package com.telecombridge.gateway.validation;

import com.telecombridge.gateway.dto.ChargeRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeRequestValidatorTest {

    @Test
    void validRequest_noErrors() {
        ChargeRequest request = new ChargeRequest("+12345678", "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validMsisdn_maxLength() {
        ChargeRequest request = new ChargeRequest("+123456789012345", "1", 4);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void invalidMsisdn_noPlus() {
        ChargeRequest request = new ChargeRequest("12345678", "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("msisdn"));
    }

    @Test
    void invalidMsisdn_tooShort() {
        ChargeRequest request = new ChargeRequest("+1234567", "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("msisdn"));
    }

    @Test
    void invalidMsisdn_tooLong() {
        ChargeRequest request = new ChargeRequest("+1234567890123456", "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("msisdn"));
    }

    @Test
    void invalidMsisdn_nonDigits() {
        ChargeRequest request = new ChargeRequest("+1234abcd", "123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("msisdn"));
    }

    @Test
    void invalidRequestType_zero() {
        ChargeRequest request = new ChargeRequest("+12345678", "123", 0);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("requestType"));
    }

    @Test
    void invalidRequestType_five() {
        ChargeRequest request = new ChargeRequest("+12345678", "123", 5);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("requestType"));
    }

    @Test
    void invalidServiceIdentifier_nonNumeric() {
        ChargeRequest request = new ChargeRequest("+12345678", "abc", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("serviceIdentifier"));
    }

    @Test
    void invalidServiceIdentifier_tooLong() {
        ChargeRequest request = new ChargeRequest("+12345678", "123456789012345678901234567890123", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("serviceIdentifier"));
    }

    @Test
    void invalidServiceIdentifier_empty_handledByBeanValidation() {
        // Empty string is handled by @NotBlank, so custom validator skips it
        ChargeRequest request = new ChargeRequest("+12345678", "", 1);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertTrue(errors.isEmpty());
    }

    @Test
    void multipleErrors() {
        ChargeRequest request = new ChargeRequest("invalid", "abc", 99);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertEquals(3, errors.size());
    }

    @Test
    void nullFields_skippedByCustomValidator() {
        // Null fields are handled by Bean Validation (@NotBlank, @NotNull)
        ChargeRequest request = new ChargeRequest(null, null, null);
        List<String> errors = ChargeRequestValidator.validate(request);
        assertTrue(errors.isEmpty());
    }
}
