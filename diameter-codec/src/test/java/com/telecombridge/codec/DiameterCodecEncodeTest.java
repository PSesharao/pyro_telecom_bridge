package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiameterCodec#encode(DiameterMessage)}.
 */
class DiameterCodecEncodeTest {

    @Test
    void encodeEmptyMessage_producesCorrect20ByteHeader() {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0, // messageLength will be calculated
                (byte) 0xC0, // Request + Proxiable
                272, // Credit-Control
                4L, // Credit-Control Application
                0x12345678L,
                0xABCDEF01L
        );
        DiameterMessage message = new DiameterMessage(header);

        ByteBuf encoded = DiameterCodec.encode(message);
        try {
            // Total length should be 20 bytes (header only, no AVPs)
            assertEquals(20, encoded.readableBytes());

            // Byte 0: Version
            assertEquals(0x01, encoded.readByte() & 0xFF);

            // Bytes 1-3: Message Length = 20
            int msgLength = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);
            assertEquals(20, msgLength);

            // Byte 4: Command Flags
            assertEquals(0xC0, encoded.readByte() & 0xFF);

            // Bytes 5-7: Command Code = 272
            int cmdCode = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);
            assertEquals(272, cmdCode);

            // Bytes 8-11: Application-ID = 4
            assertEquals(4, encoded.readInt());

            // Bytes 12-15: Hop-by-Hop-ID
            assertEquals(0x12345678, encoded.readInt());

            // Bytes 16-19: End-to-End-ID
            assertEquals((int) 0xABCDEF01L, encoded.readInt());
        } finally {
            encoded.release();
        }
    }

    @Test
    void encodeMessageWithAvps_calculatesCorrectTotalLength() {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0,
                (byte) 0xC0,
                272,
                4L,
                1L,
                2L
        );

        // Create an AVP with 4 bytes of data (no padding needed)
        // AVP header (8 bytes) + data (4 bytes) = 12 bytes total
        byte[] data = new byte[]{0x00, 0x00, 0x07, (byte) 0xD1}; // Result-Code 2001
        Avp avp = new Avp(AvpCodes.RESULT_CODE, Avp.FLAG_MANDATORY, data);

        DiameterMessage message = new DiameterMessage(header, List.of(avp));

        ByteBuf encoded = DiameterCodec.encode(message);
        try {
            // Total: 20 (header) + 12 (AVP: 8 header + 4 data) = 32 bytes
            assertEquals(32, encoded.readableBytes());

            // Check Message Length in header (bytes 1-3)
            encoded.skipBytes(1); // skip version
            int msgLength = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);
            assertEquals(32, msgLength);
        } finally {
            encoded.release();
        }
    }

    @Test
    void encodeMessageWithPaddedAvp_includesPaddingInTotalLength() {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0,
                (byte) 0x80,
                257, // CER
                0L,
                100L,
                200L
        );

        // Create an AVP with 5 bytes of data (needs 3 bytes padding to reach 4-byte boundary)
        // AVP header (8) + data (5) = 13 bytes AVP Length, padded to 16 bytes on wire
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        Avp avp = new Avp(AvpCodes.ORIGIN_HOST, Avp.FLAG_MANDATORY, data);

        DiameterMessage message = new DiameterMessage(header, List.of(avp));

        ByteBuf encoded = DiameterCodec.encode(message);
        try {
            // Total: 20 (header) + 16 (AVP: 8 header + 5 data + 3 padding) = 36 bytes
            assertEquals(36, encoded.readableBytes());

            // Check Message Length in header includes padding
            encoded.skipBytes(1); // skip version
            int msgLength = ((encoded.readByte() & 0xFF) << 16)
                    | ((encoded.readByte() & 0xFF) << 8)
                    | (encoded.readByte() & 0xFF);
            assertEquals(36, msgLength);
        } finally {
            encoded.release();
        }
    }

    @Test
    void encodeMessageWithMultipleAvps_encodesInOrder() {
        DiameterHeader header = new DiameterHeader(
                DiameterHeader.DIAMETER_VERSION,
                0,
                (byte) 0xC0,
                272,
                4L,
                42L,
                99L
        );

        // Two AVPs with 4-byte data each (no padding)
        byte[] data1 = new byte[]{0x00, 0x00, 0x00, 0x04}; // Auth-Application-Id = 4
        Avp avp1 = new Avp(AvpCodes.AUTH_APPLICATION_ID, Avp.FLAG_MANDATORY, data1);

        byte[] data2 = new byte[]{0x00, 0x00, 0x00, 0x01}; // CC-Request-Type = 1
        Avp avp2 = new Avp(AvpCodes.CC_REQUEST_TYPE, Avp.FLAG_MANDATORY, data2);

        DiameterMessage message = new DiameterMessage(header, List.of(avp1, avp2));

        ByteBuf encoded = DiameterCodec.encode(message);
        try {
            // Total: 20 + 12 + 12 = 44 bytes
            assertEquals(44, encoded.readableBytes());

            // Skip header (20 bytes)
            encoded.skipBytes(20);

            // First AVP: code should be AUTH_APPLICATION_ID (258)
            int code1 = encoded.readInt();
            assertEquals(AvpCodes.AUTH_APPLICATION_ID, code1);

            // Skip rest of first AVP (flags + length + data = 8 bytes)
            encoded.skipBytes(8);

            // Second AVP: code should be CC_REQUEST_TYPE (416)
            int code2 = encoded.readInt();
            assertEquals(AvpCodes.CC_REQUEST_TYPE, code2);
        } finally {
            encoded.release();
        }
    }
}
