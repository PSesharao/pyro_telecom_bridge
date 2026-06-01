package com.telecombridge.codec;

/**
 * Diameter message header (20 bytes on the wire).
 *
 * @param version        Protocol version, always 0x01
 * @param messageLength  Total message length in bytes (3 bytes on wire)
 * @param commandFlags   Command flags: R=0x80, P=0x40, E=0x20, T=0x10
 * @param commandCode    Command code (3 bytes on wire): 272=CC, 257=CE, 280=DW
 * @param applicationId  Application ID (4 bytes): 4=Credit-Control, 0=Common
 * @param hopByHopId     Hop-by-Hop identifier (4 bytes), unique per connection
 * @param endToEndId     End-to-End identifier (4 bytes), unique globally
 */
public record DiameterHeader(
        byte version,
        int messageLength,
        byte commandFlags,
        int commandCode,
        long applicationId,
        long hopByHopId,
        long endToEndId
) {

    /** Request flag bit mask. */
    public static final byte FLAG_REQUEST = (byte) 0x80;

    /** Proxiable flag bit mask. */
    public static final byte FLAG_PROXIABLE = (byte) 0x40;

    /** Error flag bit mask. */
    public static final byte FLAG_ERROR = (byte) 0x20;

    /** Potentially re-transmitted flag bit mask. */
    public static final byte FLAG_RETRANSMIT = (byte) 0x10;

    /** Diameter protocol version. */
    public static final byte DIAMETER_VERSION = (byte) 0x01;

    /** Header size in bytes. */
    public static final int HEADER_SIZE = 20;
}
