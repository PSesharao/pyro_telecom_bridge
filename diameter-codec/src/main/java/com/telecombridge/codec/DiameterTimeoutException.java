package com.telecombridge.codec;

/**
 * Thrown when a Diameter request times out waiting for a response.
 */
public class DiameterTimeoutException extends DiameterException {

    public DiameterTimeoutException(String message) {
        super(message);
    }

    public DiameterTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
