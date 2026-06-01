package com.telecombridge.codec;

/**
 * Thrown when a Diameter message or AVP cannot be encoded (e.g., AVP value
 * exceeds maximum representable length).
 */
public class DiameterEncodingException extends DiameterException {

    public DiameterEncodingException(String message) {
        super(message);
    }

    public DiameterEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
