package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes Diameter AVPs into Netty ByteBuf according to RFC 6733.
 * <p>
 * AVP wire format:
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           AVP Code                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V M P r r r r r|                  AVP Length                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Vendor-ID (opt)                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |    Data ...
 * +-+-+-+-+-+-+-+-+
 * </pre>
 * Padding zeros are appended to align to a 4-byte boundary but are NOT
 * included in the AVP Length field.
 */
public final class AvpEncoder {

    /**
     * Maximum AVP data length for non-vendor-specific AVPs.
     * AVP Length field is 3 bytes (max 2^24 - 1 = 16777215), minus 8-byte header = 16777207.
     */
    public static final int MAX_DATA_LENGTH_NON_VENDOR = (1 << 24) - 1 - Avp.HEADER_SIZE;

    /**
     * Maximum AVP data length for vendor-specific AVPs.
     * AVP Length field is 3 bytes (max 2^24 - 1 = 16777215), minus 12-byte header = 16777203.
     */
    public static final int MAX_DATA_LENGTH_VENDOR = (1 << 24) - 1 - Avp.HEADER_SIZE_VENDOR;

    private AvpEncoder() {
        // Utility class — no instantiation
    }

    /**
     * Encodes an AVP into the given ByteBuf.
     *
     * @param avp    the AVP to encode
     * @param buffer the target buffer to write into
     * @throws DiameterEncodingException if the AVP value exceeds the maximum length
     */
    public static void encode(Avp avp, ByteBuf buffer) {
        byte[] data = avp.getDataInternal();
        boolean vendorSpecific = avp.isVendorSpecific();
        int headerSize = vendorSpecific ? Avp.HEADER_SIZE_VENDOR : Avp.HEADER_SIZE;
        int maxDataLength = vendorSpecific ? MAX_DATA_LENGTH_VENDOR : MAX_DATA_LENGTH_NON_VENDOR;

        if (data.length > maxDataLength) {
            throw new DiameterEncodingException(
                    "AVP code " + avp.getCode() + " data length " + data.length
                            + " exceeds maximum " + maxDataLength + " bytes"
                            + (vendorSpecific ? " for vendor-specific AVP" : " for non-vendor AVP"));
        }

        int avpLength = headerSize + data.length;

        // AVP Code (4 bytes, big-endian)
        buffer.writeInt(avp.getCode());

        // Flags (1 byte) + Length (3 bytes) packed into 4 bytes
        buffer.writeByte(avp.getFlags());
        // Length as 3 bytes big-endian
        buffer.writeByte((avpLength >> 16) & 0xFF);
        buffer.writeByte((avpLength >> 8) & 0xFF);
        buffer.writeByte(avpLength & 0xFF);

        // Vendor-ID (4 bytes, only if V flag set)
        if (vendorSpecific) {
            buffer.writeInt(avp.getVendorId());
        }

        // Data
        buffer.writeBytes(data);

        // Padding to 4-byte boundary (not included in AVP Length)
        int padding = (4 - (avpLength % 4)) % 4;
        for (int i = 0; i < padding; i++) {
            buffer.writeByte(0);
        }
    }

    /**
     * Encodes an AVP into a newly allocated ByteBuf.
     *
     * @param avp the AVP to encode
     * @return a ByteBuf containing the encoded AVP (caller must release)
     * @throws DiameterEncodingException if the AVP value exceeds the maximum length
     */
    public static ByteBuf encode(Avp avp) {
        byte[] data = avp.getDataInternal();
        boolean vendorSpecific = avp.isVendorSpecific();
        int headerSize = vendorSpecific ? Avp.HEADER_SIZE_VENDOR : Avp.HEADER_SIZE;
        int avpLength = headerSize + data.length;
        int paddedLength = avpLength + ((4 - (avpLength % 4)) % 4);

        ByteBuf buffer = Unpooled.buffer(paddedLength);
        try {
            encode(avp, buffer);
            return buffer;
        } catch (Exception e) {
            buffer.release();
            throw e;
        }
    }

    /**
     * Encodes an OctetString AVP value (raw bytes).
     *
     * @param code       the AVP code
     * @param flags      the AVP flags
     * @param vendorId   the vendor ID (0 if not vendor-specific)
     * @param data       the raw byte data
     * @param buffer     the target buffer
     * @throws DiameterEncodingException if the data exceeds the maximum length
     */
    public static void encodeOctetString(int code, byte flags, int vendorId, byte[] data, ByteBuf buffer) {
        Avp avp = new Avp(code, flags, vendorId, data);
        encode(avp, buffer);
    }

    /**
     * Encodes an Unsigned32 AVP value (4 bytes, big-endian).
     *
     * @param code       the AVP code
     * @param flags      the AVP flags
     * @param vendorId   the vendor ID (0 if not vendor-specific)
     * @param value      the unsigned 32-bit value (as long to handle full range)
     * @param buffer     the target buffer
     */
    public static void encodeUnsigned32(int code, byte flags, int vendorId, long value, ByteBuf buffer) {
        byte[] data = new byte[4];
        data[0] = (byte) ((value >> 24) & 0xFF);
        data[1] = (byte) ((value >> 16) & 0xFF);
        data[2] = (byte) ((value >> 8) & 0xFF);
        data[3] = (byte) (value & 0xFF);
        Avp avp = new Avp(code, flags, vendorId, data);
        encode(avp, buffer);
    }

    /**
     * Encodes a UTF8String AVP value.
     *
     * @param code       the AVP code
     * @param flags      the AVP flags
     * @param vendorId   the vendor ID (0 if not vendor-specific)
     * @param value      the string value
     * @param buffer     the target buffer
     * @throws DiameterEncodingException if the encoded string exceeds the maximum length
     */
    public static void encodeUtf8String(int code, byte flags, int vendorId, String value, ByteBuf buffer) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        Avp avp = new Avp(code, flags, vendorId, data);
        encode(avp, buffer);
    }

    /**
     * Encodes a Grouped AVP containing nested AVPs.
     *
     * @param code       the AVP code
     * @param flags      the AVP flags
     * @param vendorId   the vendor ID (0 if not vendor-specific)
     * @param nestedAvps the list of nested AVPs to encode within this grouped AVP
     * @param buffer     the target buffer
     * @throws DiameterEncodingException if the grouped data exceeds the maximum length
     */
    public static void encodeGrouped(int code, byte flags, int vendorId, List<Avp> nestedAvps, ByteBuf buffer) {
        // First encode all nested AVPs into a temporary buffer to get the grouped data
        ByteBuf nestedBuffer = Unpooled.buffer();
        try {
            for (Avp nested : nestedAvps) {
                encode(nested, nestedBuffer);
            }
            byte[] groupedData = new byte[nestedBuffer.readableBytes()];
            nestedBuffer.readBytes(groupedData);
            Avp avp = new Avp(code, flags, vendorId, groupedData);
            encode(avp, buffer);
        } finally {
            nestedBuffer.release();
        }
    }
}
