package com.telecombridge.codec;

/**
 * Thrown when a Diameter connection error occurs (e.g., connection lost,
 * connection refused, connection timeout).
 */
public class DiameterConnectionException extends DiameterException {

    public DiameterConnectionException(String message) {
        super(message);
    }

    public DiameterConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
