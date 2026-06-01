package com.telecombridge.gateway.exception;

import java.util.List;

/**
 * Exception thrown when custom validation of a charge request fails.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
