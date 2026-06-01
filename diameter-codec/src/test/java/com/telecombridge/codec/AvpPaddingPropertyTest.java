package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for AVP Padding to 4-Byte Boundary (Property 7).
 * <p>
 * Validates: Requirements 3.3
 * <p>
 * For any AVP with data of length L (0 ≤ L ≤ 1024), the total encoded size
 * is a multiple of 4, and the AVP Length field equals header size + L.
 */
@Tag("Feature: telecom-bridge, Property 7: AVP Padding to 4-Byte Boundary")
class AvpPaddingPropertyTest {

    /**
     * **Validates: Requirements 3.3**
     * <p>
     * For any non-vendor-specific AVP with data of length L (0 ≤ L ≤ 1024),
     * the total encoded size is a multiple of 4, and AVP Length = 8 + L.
     */
    @Property(tries = 200)
    void totalEncodedSizeIsMultipleOf4_nonVendor(@ForAll @IntRange(min = 0, max = 1024) int dataLength) {
        byte[] data = new byte[dataLength];
        Avp avp = new Avp(AvpCodes.SESSION_ID, Avp.FLAG_MANDATORY, data);

        ByteBuf encoded = AvpEncoder.encode(avp);
        try {
            int totalEncodedSize = encoded.readableBytes();

            // Total encoded size must be a multiple of 4
            assertEquals(0, totalEncodedSize % 4,
                    "Total encoded size " + totalEncodedSize + " is not a multiple of 4 for data length " + dataLength);

            // AVP Length field (bytes 5-7) should equal header size (8) + data length
            int expectedAvpLength = Avp.HEADER_SIZE + dataLength;

            // Read AVP Length from the encoded buffer (skip 4 bytes AVP Code + 1 byte flags)
            encoded.skipBytes(4); // AVP Code
            encoded.skipBytes(1); // Flags byte
            int avpLength = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);

            assertEquals(expectedAvpLength, avpLength,
                    "AVP Length field should be header(8) + dataLength(" + dataLength + ") = " + expectedAvpLength);
        } finally {
            encoded.release();
        }
    }

    /**
     * **Validates: Requirements 3.3**
     * <p>
     * For any vendor-specific AVP with data of length L (0 ≤ L ≤ 1024),
     * the total encoded size is a multiple of 4, and AVP Length = 12 + L.
     */
    @Property(tries = 200)
    void totalEncodedSizeIsMultipleOf4_vendorSpecific(@ForAll @IntRange(min = 0, max = 1024) int dataLength) {
        byte[] data = new byte[dataLength];
        byte flags = (byte) (Avp.FLAG_VENDOR | Avp.FLAG_MANDATORY);
        Avp avp = new Avp(AvpCodes.SUBSCRIPTION_ID, flags, 10415, data);

        ByteBuf encoded = AvpEncoder.encode(avp);
        try {
            int totalEncodedSize = encoded.readableBytes();

            // Total encoded size must be a multiple of 4
            assertEquals(0, totalEncodedSize % 4,
                    "Total encoded size " + totalEncodedSize + " is not a multiple of 4 for data length " + dataLength);

            // AVP Length field should equal header size (12) + data length
            int expectedAvpLength = Avp.HEADER_SIZE_VENDOR + dataLength;

            // Read AVP Length from the encoded buffer (skip 4 bytes AVP Code + 1 byte flags)
            encoded.skipBytes(4); // AVP Code
            encoded.skipBytes(1); // Flags byte
            int avpLength = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);

            assertEquals(expectedAvpLength, avpLength,
                    "AVP Length field should be header(12) + dataLength(" + dataLength + ") = " + expectedAvpLength);
        } finally {
            encoded.release();
        }
    }
}
