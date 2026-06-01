package com.telecombridge.codec;

/**
 * Base exception for all Diameter protocol errors.
 */
public class DiameterException extends RuntimeException {

    public DiameterException(String message) {
        super(message);
    }

    public DiameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
