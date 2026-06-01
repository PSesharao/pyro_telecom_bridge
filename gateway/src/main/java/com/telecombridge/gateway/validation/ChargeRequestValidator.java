package com.telecombridge.gateway.validation;

import com.telecombridge.gateway.dto.ChargeRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Custom validator for ChargeRequest fields beyond basic Bean Validation.
 * Validates MSISDN format, requestType range, and serviceIdentifier format.
 */
public final class ChargeRequestValidator {

    private static final Pattern MSISDN_PATTERN = Pattern.compile("^\\+\\d{8,15}$");
    private static final Pattern SERVICE_IDENTIFIER_PATTERN = Pattern.compile("^\\d{1,32}$");
    private static final Set<Integer> VALID_REQUEST_TYPES = Set.of(1, 2, 3, 4);

    private ChargeRequestValidator() {
    }

    /**
     * Validates the charge request fields and returns a list of error messages.
     * Returns an empty list if all fields are valid.
     */
    public static List<String> validate(ChargeRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.msisdn() != null && !request.msisdn().isBlank()) {
            if (!MSISDN_PATTERN.matcher(request.msisdn()).matches()) {
                errors.add("msisdn: must be in E.164 format (leading '+' followed by 8 to 15 digits)");
            }
        }

        if (request.requestType() != null) {
            if (!VALID_REQUEST_TYPES.contains(request.requestType())) {
                errors.add("requestType: must be one of 1 (INITIAL), 2 (UPDATE), 3 (TERMINATION), or 4 (EVENT)");
            }
        }

        if (request.serviceIdentifier() != null && !request.serviceIdentifier().isBlank()) {
            if (!SERVICE_IDENTIFIER_PATTERN.matcher(request.serviceIdentifier()).matches()) {
                errors.add("serviceIdentifier: must be a numeric string of 1 to 32 digits");
            }
        }

        return errors;
    }
}
