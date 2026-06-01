package com.telecombridge.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Diameter Attribute-Value Pair (AVP).
 * <p>
 * An AVP consists of a code, flags, optional vendor ID, and raw data bytes.
 * Typed accessors are provided for common AVP data formats.
 */
public class Avp {

    /** Vendor-Specific flag bit mask. */
    public static final byte FLAG_VENDOR = (byte) 0x80;

    /** Mandatory flag bit mask. */
    public static final byte FLAG_MANDATORY = (byte) 0x40;

    /** Protected flag bit mask. */
    public static final byte FLAG_PROTECTED = (byte) 0x20;

    /** AVP header size without vendor ID. */
    public static final int HEADER_SIZE = 8;

    /** AVP header size with vendor ID. */
    public static final int HEADER_SIZE_VENDOR = 12;

    private final int code;
    private final byte flags;
    private final int vendorId;
    private final byte[] data;

    /**
     * Creates a new AVP with all fields specified.
     *
     * @param code     the AVP code
     * @param flags    the AVP flags (V=0x80, M=0x40, P=0x20)
     * @param vendorId the vendor ID (relevant only if V flag is set)
     * @param data     the raw AVP data bytes
     */
    public Avp(int code, byte flags, int vendorId, byte[] data) {
        this.code = code;
        this.flags = flags;
        this.vendorId = vendorId;
        this.data = data != null ? Arrays.copyOf(data, data.length) : new byte[0];
    }

    /**
     * Creates a new non-vendor-specific AVP.
     *
     * @param code  the AVP code
     * @param flags the AVP flags
     * @param data  the raw AVP data bytes
     */
    public Avp(int code, byte flags, byte[] data) {
        this(code, flags, 0, data);
    }

    /**
     * Returns the AVP code.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the AVP flags byte.
     */
    public byte getFlags() {
        return flags;
    }

    /**
     * Returns the vendor ID. Only meaningful if {@link #isVendorSpecific()} is true.
     */
    public int getVendorId() {
        return vendorId;
    }

    /**
     * Returns a copy of the raw data bytes.
     */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Returns the raw data bytes without copying (for internal codec use).
     */
    byte[] getDataInternal() {
        return data;
    }

    /**
     * Returns true if the Vendor-Specific flag (V) is set.
     */
    public boolean isVendorSpecific() {
        return (flags & FLAG_VENDOR) != 0;
    }

    /**
     * Returns true if the Mandatory flag (M) is set.
     */
    public boolean isMandatory() {
        return (flags & FLAG_MANDATORY) != 0;
    }

    /**
     * Interprets the data as an Unsigned32 value (4 bytes, big-endian).
     *
     * @return the unsigned 32-bit value as a long
     * @throws DiameterProtocolException if data is not exactly 4 bytes
     */
    public long asUnsigned32() {
        if (data.length != 4) {
            throw new DiameterProtocolException(
                    "AVP code " + code + " data length " + data.length + " is not 4 bytes for Unsigned32");
        }
        return ((long) (data[0] & 0xFF) << 24)
                | ((long) (data[1] & 0xFF) << 16)
                | ((long) (data[2] & 0xFF) << 8)
                | ((long) (data[3] & 0xFF));
    }

    /**
     * Interprets the data as a UTF-8 encoded string.
     *
     * @return the decoded string
     */
    public String asUtf8String() {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Returns the data as an OctetString (raw bytes).
     *
     * @return a copy of the raw data bytes
     */
    public byte[] asOctetString() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Interprets the data as a Grouped AVP, parsing nested AVPs from the data bytes.
     *
     * @return a list of nested AVPs
     * @throws DiameterProtocolException if the grouped data cannot be parsed
     */
    public List<Avp> asGrouped() {
        List<Avp> nested = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(data);

        while (buffer.remaining() >= HEADER_SIZE) {
            int avpCode = buffer.getInt();
            byte avpFlags = buffer.get();
            // Length is 3 bytes
            int avpLength = ((buffer.get() & 0xFF) << 16)
                    | ((buffer.get() & 0xFF) << 8)
                    | (buffer.get() & 0xFF);

            boolean vendorSpecific = (avpFlags & FLAG_VENDOR) != 0;
            int headerSize = vendorSpecific ? HEADER_SIZE_VENDOR : HEADER_SIZE;

            if (avpLength < headerSize) {
                throw new DiameterProtocolException(
                        "Grouped AVP: nested AVP code " + avpCode + " has invalid length " + avpLength);
            }

            int avpVendorId = 0;
            if (vendorSpecific) {
                if (buffer.remaining() < 4) {
                    throw new DiameterProtocolException(
                            "Grouped AVP: insufficient data for vendor ID in nested AVP code " + avpCode);
                }
                avpVendorId = buffer.getInt();
            }

            int dataLength = avpLength - headerSize;
            if (buffer.remaining() < dataLength) {
                throw new DiameterProtocolException(
                        "Grouped AVP: insufficient data for nested AVP code " + avpCode);
            }

            byte[] avpData = new byte[dataLength];
            buffer.get(avpData);

            nested.add(new Avp(avpCode, avpFlags, avpVendorId, avpData));

            // Skip padding to 4-byte boundary
            int padding = (4 - (avpLength % 4)) % 4;
            if (buffer.remaining() >= padding) {
                buffer.position(buffer.position() + padding);
            }
        }

        return nested;
    }

    @Override
    public String toString() {
        return "Avp{code=" + code + ", flags=0x" + String.format("%02X", flags)
                + ", vendorId=" + vendorId + ", dataLength=" + data.length + "}";
    }
}
