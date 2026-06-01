package com.telecombridge.codec;

/**
 * Thrown when a Diameter protocol violation is detected (e.g., malformed message,
 * missing required AVPs, invalid field values).
 */
public class DiameterProtocolException extends DiameterException {

    public DiameterProtocolException(String message) {
        super(message);
    }

    public DiameterProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
