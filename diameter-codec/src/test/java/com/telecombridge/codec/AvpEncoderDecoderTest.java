package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AVP encoding ({@link AvpEncoder}) and decoding ({@link AvpDecoder}).
 * Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.6
 */
class AvpEncoderDecoderTest {

    private final AvpDecoder decoder = new AvpDecoder();

    // =========================================================================
    // Encoding Tests - Requirement 13.1
    // =========================================================================

    @Nested
    @DisplayName("OctetString Encoding")
    class OctetStringEncodingTests {

        @Test
        @DisplayName("Encode OctetString with 4 bytes produces correct byte sequence")
        void encodeOctetString_4bytes() {
            byte[] data = new byte[]{0x0A, 0x01, 0x02, 0x03};
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeOctetString(
                        AvpCodes.HOST_IP_ADDRESS, Avp.FLAG_MANDATORY, 0, data, buffer);

                // Expected: Code(4) + Flags(1) + Length(3) + Data(4) = 12 bytes
                assertEquals(12, buffer.readableBytes());
                // AVP Code = 257 (HOST_IP_ADDRESS)
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                // Flags = 0x40 (Mandatory)
                assertEquals(0x40, buffer.readByte() & 0xFF);
                // Length = 12 (8 header + 4 data)
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x0C, buffer.readByte() & 0xFF);
                // Data
                assertEquals(0x0A, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0x02, buffer.readByte() & 0xFF);
                assertEquals(0x03, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Encode OctetString with 6 bytes produces padded output")
        void encodeOctetString_6bytes_withPadding() {
            byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeOctetString(
                        AvpCodes.HOST_IP_ADDRESS, Avp.FLAG_MANDATORY, 0, data, buffer);

                // AVP Length = 8 + 6 = 14, padded to 16
                assertEquals(16, buffer.readableBytes());
                // Skip code (4) + flags (1) + length (3) = 8 bytes
                buffer.skipBytes(8);
                // Data bytes
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0x02, buffer.readByte() & 0xFF);
                assertEquals(0x03, buffer.readByte() & 0xFF);
                assertEquals(0x04, buffer.readByte() & 0xFF);
                assertEquals(0x05, buffer.readByte() & 0xFF);
                assertEquals(0x06, buffer.readByte() & 0xFF);
                // Padding (2 bytes of zeros)
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("Unsigned32 Encoding")
    class Unsigned32EncodingTests {

        @Test
        @DisplayName("Encode Unsigned32 value 2001 produces correct byte sequence")
        void encodeUnsigned32_value2001() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeUnsigned32(
                        AvpCodes.RESULT_CODE, Avp.FLAG_MANDATORY, 0, 2001L, buffer);

                // Expected: Code(4) + Flags(1) + Length(3) + Data(4) = 12 bytes, no padding
                assertEquals(12, buffer.readableBytes());
                // AVP Code = 268 (RESULT_CODE) = 0x0000010C
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0x0C, buffer.readByte() & 0xFF);
                // Flags = 0x40 (Mandatory)
                assertEquals(0x40, buffer.readByte() & 0xFF);
                // Length = 12
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x0C, buffer.readByte() & 0xFF);
                // Data = 2001 = 0x000007D1
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x07, buffer.readByte() & 0xFF);
                assertEquals(0xD1, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Encode Unsigned32 max value 0xFFFFFFFF produces correct bytes")
        void encodeUnsigned32_maxValue() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeUnsigned32(
                        AvpCodes.CC_REQUEST_NUMBER, Avp.FLAG_MANDATORY, 0, 0xFFFFFFFFL, buffer);

                assertEquals(12, buffer.readableBytes());
                // Skip header (8 bytes)
                buffer.skipBytes(8);
                // Data = 0xFFFFFFFF
                assertEquals(0xFF, buffer.readByte() & 0xFF);
                assertEquals(0xFF, buffer.readByte() & 0xFF);
                assertEquals(0xFF, buffer.readByte() & 0xFF);
                assertEquals(0xFF, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("UTF8String Encoding")
    class Utf8StringEncodingTests {

        @Test
        @DisplayName("Encode UTF8String 'CTOPUP' produces correct byte sequence")
        void encodeUtf8String_ctopup() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeUtf8String(
                        AvpCodes.ORIGIN_HOST, Avp.FLAG_MANDATORY, 0, "CTOPUP", buffer);

                // "CTOPUP" = 6 bytes UTF-8, AVP Length = 8 + 6 = 14, padded to 16
                assertEquals(16, buffer.readableBytes());
                // AVP Code = 264 (ORIGIN_HOST) = 0x00000108
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0x08, buffer.readByte() & 0xFF);
                // Flags = 0x40
                assertEquals(0x40, buffer.readByte() & 0xFF);
                // Length = 14 = 0x00000E
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x0E, buffer.readByte() & 0xFF);
                // Data = "CTOPUP" in ASCII/UTF-8
                assertEquals('C', buffer.readByte() & 0xFF);
                assertEquals('T', buffer.readByte() & 0xFF);
                assertEquals('O', buffer.readByte() & 0xFF);
                assertEquals('P', buffer.readByte() & 0xFF);
                assertEquals('U', buffer.readByte() & 0xFF);
                assertEquals('P', buffer.readByte() & 0xFF);
                // Padding (2 bytes)
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Encode UTF8String 'test' (4 bytes, no padding needed)")
        void encodeUtf8String_test_noPadding() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeUtf8String(
                        AvpCodes.ORIGIN_REALM, Avp.FLAG_MANDATORY, 0, "test", buffer);

                // "test" = 4 bytes, AVP Length = 8 + 4 = 12, already aligned
                assertEquals(12, buffer.readableBytes());
                buffer.skipBytes(8); // skip header
                assertEquals('t', buffer.readByte() & 0xFF);
                assertEquals('e', buffer.readByte() & 0xFF);
                assertEquals('s', buffer.readByte() & 0xFF);
                assertEquals('t', buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("Grouped AVP Encoding")
    class GroupedEncodingTests {

        @Test
        @DisplayName("Encode Grouped AVP with two nested Unsigned32 AVPs")
        void encodeGrouped_twoNestedAvps() {
            // Subscription-Id grouped AVP containing Type and Data
            Avp typeAvp = new Avp(AvpCodes.SUBSCRIPTION_ID_TYPE, Avp.FLAG_MANDATORY,
                    new byte[]{0x00, 0x00, 0x00, 0x00}); // END_USER_E164 = 0
            Avp dataAvp = new Avp(AvpCodes.SUBSCRIPTION_ID_DATA, Avp.FLAG_MANDATORY,
                    "+1234567890".getBytes(StandardCharsets.UTF_8));

            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeGrouped(
                        AvpCodes.SUBSCRIPTION_ID, Avp.FLAG_MANDATORY, 0,
                        List.of(typeAvp, dataAvp), buffer);

                // Nested AVP 1: code(4)+flags(1)+len(3)+data(4) = 12 bytes
                // Nested AVP 2: code(4)+flags(1)+len(3)+data(11) = 19 bytes, padded to 20
                // Grouped data = 12 + 20 = 32 bytes
                // Outer: code(4)+flags(1)+len(3)+groupedData(32) = 40 bytes
                assertEquals(40, buffer.readableBytes());

                // Verify outer AVP code = 443 (SUBSCRIPTION_ID) = 0x000001BB
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x01, buffer.readByte() & 0xFF);
                assertEquals(0xBB, buffer.readByte() & 0xFF);
                // Flags = 0x40
                assertEquals(0x40, buffer.readByte() & 0xFF);
                // Length = 8 + 32 = 40 = 0x000028
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x00, buffer.readByte() & 0xFF);
                assertEquals(0x28, buffer.readByte() & 0xFF);
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Encode Grouped AVP with single nested AVP")
        void encodeGrouped_singleNestedAvp() {
            Avp nested = new Avp(AvpCodes.RESULT_CODE, Avp.FLAG_MANDATORY,
                    new byte[]{0x00, 0x00, 0x07, (byte) 0xD1}); // 2001

            ByteBuf buffer = Unpooled.buffer();
            try {
                AvpEncoder.encodeGrouped(
                        AvpCodes.GRANTED_SERVICE_UNIT, Avp.FLAG_MANDATORY, 0,
                        List.of(nested), buffer);

                // Nested: 12 bytes (8 header + 4 data, no padding)
                // Outer: 8 + 12 = 20 bytes
                assertEquals(20, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    // =========================================================================
    // Decoding Tests - Requirement 13.2
    // =========================================================================

    @Nested
    @DisplayName("OctetString Decoding")
    class OctetStringDecodingTests {

        @Test
        @DisplayName("Decode OctetString AVP from known byte sequence")
        void decodeOctetString_knownBytes() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code = 257 (HOST_IP_ADDRESS)
                buffer.writeInt(257);
                // Flags = 0x40 (Mandatory)
                buffer.writeByte(0x40);
                // Length = 12 (8 header + 4 data)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x0C);
                // Data: 4 bytes (IPv4 address 10.1.2.3)
                buffer.writeByte(0x0A);
                buffer.writeByte(0x01);
                buffer.writeByte(0x02);
                buffer.writeByte(0x03);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                Avp avp = result.get();
                assertEquals(257, avp.getCode());
                assertArrayEquals(new byte[]{0x0A, 0x01, 0x02, 0x03}, avp.asOctetString());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Decode OctetString AVP with padding from known bytes")
        void decodeOctetString_withPadding() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code = 257
                buffer.writeInt(257);
                // Flags = 0x40
                buffer.writeByte(0x40);
                // Length = 13 (8 header + 5 data)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x0D);
                // Data: 5 bytes
                buffer.writeBytes(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
                // Padding: 3 bytes to reach 4-byte boundary
                buffer.writeBytes(new byte[]{0x00, 0x00, 0x00});

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                Avp avp = result.get();
                assertEquals(257, avp.getCode());
                assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05}, avp.asOctetString());
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("Unsigned32 Decoding")
    class Unsigned32DecodingTests {

        @Test
        @DisplayName("Decode Unsigned32 value 2001 from known bytes")
        void decodeUnsigned32_value2001() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code = 268 (RESULT_CODE)
                buffer.writeInt(268);
                // Flags = 0x40
                buffer.writeByte(0x40);
                // Length = 12
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x0C);
                // Data = 2001 = 0x000007D1
                buffer.writeInt(2001);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                Avp avp = result.get();
                assertEquals(268, avp.getCode());
                assertEquals(2001L, avp.asUnsigned32());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Decode Unsigned32 max value 0xFFFFFFFF from known bytes")
        void decodeUnsigned32_maxValue() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                buffer.writeInt(AvpCodes.CC_REQUEST_NUMBER);
                buffer.writeByte(0x40);
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x0C);
                buffer.writeInt(0xFFFFFFFF);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                assertEquals(0xFFFFFFFFL, result.get().asUnsigned32());
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("UTF8String Decoding")
    class Utf8StringDecodingTests {

        @Test
        @DisplayName("Decode UTF8String 'CTOPUP' from known bytes")
        void decodeUtf8String_ctopup() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                buffer.writeInt(AvpCodes.ORIGIN_HOST); // 264
                buffer.writeByte(0x40);
                // Length = 8 + 6 = 14
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x0E);
                buffer.writeBytes("CTOPUP".getBytes(StandardCharsets.UTF_8));
                // Padding to 4-byte boundary (14 -> 16, 2 bytes padding)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                assertEquals("CTOPUP", result.get().asUtf8String());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Decode UTF8String 'ctop.com' from known bytes")
        void decodeUtf8String_realm() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                buffer.writeInt(AvpCodes.ORIGIN_REALM); // 296
                buffer.writeByte(0x40);
                // Length = 8 + 8 = 16 (aligned, no padding)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x10);
                buffer.writeBytes("ctop.com".getBytes(StandardCharsets.UTF_8));

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                assertEquals("ctop.com", result.get().asUtf8String());
            } finally {
                buffer.release();
            }
        }
    }

    @Nested
    @DisplayName("Grouped AVP Decoding")
    class GroupedDecodingTests {

        @Test
        @DisplayName("Decode Grouped AVP with two nested AVPs")
        void decodeGrouped_twoNestedAvps() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // Build grouped data: two nested AVPs
                // Nested 1: Subscription-Id-Type (450), Unsigned32 = 0
                // Nested 2: Subscription-Id-Data (444), UTF8String = "+1234"
                ByteBuf nestedBuf = Unpooled.buffer();
                AvpEncoder.encodeUnsigned32(AvpCodes.SUBSCRIPTION_ID_TYPE,
                        Avp.FLAG_MANDATORY, 0, 0L, nestedBuf);
                AvpEncoder.encodeUtf8String(AvpCodes.SUBSCRIPTION_ID_DATA,
                        Avp.FLAG_MANDATORY, 0, "+1234", nestedBuf);

                byte[] groupedData = new byte[nestedBuf.readableBytes()];
                nestedBuf.readBytes(groupedData);
                nestedBuf.release();

                // Outer AVP: Subscription-Id (443)
                buffer.writeInt(AvpCodes.SUBSCRIPTION_ID);
                buffer.writeByte(0x40);
                int avpLength = 8 + groupedData.length;
                buffer.writeByte((avpLength >> 16) & 0xFF);
                buffer.writeByte((avpLength >> 8) & 0xFF);
                buffer.writeByte(avpLength & 0xFF);
                buffer.writeBytes(groupedData);
                // Add padding if needed
                int padding = (4 - (avpLength % 4)) % 4;
                for (int i = 0; i < padding; i++) {
                    buffer.writeByte(0);
                }

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                Avp avp = result.get();
                assertEquals(AvpCodes.SUBSCRIPTION_ID, avp.getCode());

                List<Avp> nested = avp.asGrouped();
                assertEquals(2, nested.size());
                assertEquals(AvpCodes.SUBSCRIPTION_ID_TYPE, nested.get(0).getCode());
                assertEquals(0L, nested.get(0).asUnsigned32());
                assertEquals(AvpCodes.SUBSCRIPTION_ID_DATA, nested.get(1).getCode());
                assertEquals("+1234", nested.get(1).asUtf8String());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Decode Grouped AVP with single nested AVP")
        void decodeGrouped_singleNestedAvp() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // Build nested: Result-Code = 2001
                ByteBuf nestedBuf = Unpooled.buffer();
                AvpEncoder.encodeUnsigned32(AvpCodes.RESULT_CODE,
                        Avp.FLAG_MANDATORY, 0, 2001L, nestedBuf);
                byte[] groupedData = new byte[nestedBuf.readableBytes()];
                nestedBuf.readBytes(groupedData);
                nestedBuf.release();

                buffer.writeInt(AvpCodes.GRANTED_SERVICE_UNIT);
                buffer.writeByte(0x40);
                int avpLength = 8 + groupedData.length;
                buffer.writeByte((avpLength >> 16) & 0xFF);
                buffer.writeByte((avpLength >> 8) & 0xFF);
                buffer.writeByte(avpLength & 0xFF);
                buffer.writeBytes(groupedData);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                List<Avp> nested = result.get().asGrouped();
                assertEquals(1, nested.size());
                assertEquals(2001L, nested.get(0).asUnsigned32());
            } finally {
                buffer.release();
            }
        }
    }

    // =========================================================================
    // Padding Tests - Requirement 13.3
    // =========================================================================

    @Nested
    @DisplayName("AVP Padding to 4-Byte Boundary")
    class PaddingTests {

        @Test
        @DisplayName("1-byte data: total encoded length is multiple of 4")
        void padding_1byte() {
            assertPaddedToMultipleOf4(1);
        }

        @Test
        @DisplayName("2-byte data: total encoded length is multiple of 4")
        void padding_2bytes() {
            assertPaddedToMultipleOf4(2);
        }

        @Test
        @DisplayName("3-byte data: total encoded length is multiple of 4")
        void padding_3bytes() {
            assertPaddedToMultipleOf4(3);
        }

        @Test
        @DisplayName("4-byte data: total encoded length is multiple of 4")
        void padding_4bytes() {
            assertPaddedToMultipleOf4(4);
        }

        @Test
        @DisplayName("5-byte data: total encoded length is multiple of 4")
        void padding_5bytes() {
            assertPaddedToMultipleOf4(5);
        }

        @Test
        @DisplayName("6-byte data: total encoded length is multiple of 4")
        void padding_6bytes() {
            assertPaddedToMultipleOf4(6);
        }

        @Test
        @DisplayName("7-byte data: total encoded length is multiple of 4")
        void padding_7bytes() {
            assertPaddedToMultipleOf4(7);
        }

        @Test
        @DisplayName("8-byte data: total encoded length is multiple of 4")
        void padding_8bytes() {
            assertPaddedToMultipleOf4(8);
        }

        private void assertPaddedToMultipleOf4(int dataLength) {
            byte[] data = new byte[dataLength];
            for (int i = 0; i < dataLength; i++) {
                data[i] = (byte) (i + 1);
            }
            Avp avp = new Avp(AvpCodes.HOST_IP_ADDRESS, Avp.FLAG_MANDATORY, data);
            ByteBuf encoded = AvpEncoder.encode(avp);
            try {
                int totalLength = encoded.readableBytes();
                assertEquals(0, totalLength % 4,
                        "Total encoded length " + totalLength
                                + " is not a multiple of 4 for data length " + dataLength);

                // Also verify AVP Length field = header + data (no padding)
                encoded.skipBytes(4); // skip code
                encoded.skipBytes(1); // skip flags
                int avpLength = ((encoded.readByte() & 0xFF) << 16)
                        | ((encoded.readByte() & 0xFF) << 8)
                        | (encoded.readByte() & 0xFF);
                assertEquals(8 + dataLength, avpLength,
                        "AVP Length should be header(8) + data(" + dataLength + ")");
            } finally {
                encoded.release();
            }
        }
    }

    // =========================================================================
    // Vendor-Specific AVP Tests - Requirement 13.4
    // =========================================================================

    @Nested
    @DisplayName("Vendor-Specific AVP (12-byte header)")
    class VendorSpecificTests {

        @Test
        @DisplayName("Encode vendor-specific AVP produces 12-byte header")
        void encodeVendorSpecific_12byteHeader() {
            byte[] data = new byte[]{0x00, 0x00, 0x00, 0x01}; // value = 1
            byte flags = (byte) (Avp.FLAG_VENDOR | Avp.FLAG_MANDATORY); // 0xC0
            int vendorId = 10415; // 3GPP vendor ID
            Avp avp = new Avp(999, flags, vendorId, data);

            ByteBuf encoded = AvpEncoder.encode(avp);
            try {
                // Expected: 12 (header with vendor) + 4 (data) = 16 bytes
                assertEquals(16, encoded.readableBytes());

                // AVP Code = 999 = 0x000003E7
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x03, encoded.readByte() & 0xFF);
                assertEquals(0xE7, encoded.readByte() & 0xFF);
                // Flags = 0xC0 (Vendor + Mandatory)
                assertEquals(0xC0, encoded.readByte() & 0xFF);
                // Length = 16 (12 header + 4 data) = 0x000010
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x10, encoded.readByte() & 0xFF);
                // Vendor-ID = 10415 = 0x000028AF
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x28, encoded.readByte() & 0xFF);
                assertEquals(0xAF, encoded.readByte() & 0xFF);
                // Data
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x00, encoded.readByte() & 0xFF);
                assertEquals(0x01, encoded.readByte() & 0xFF);
            } finally {
                encoded.release();
            }
        }

        @Test
        @DisplayName("Decode vendor-specific AVP parses 12-byte header correctly")
        void decodeVendorSpecific_12byteHeader() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code = 999
                buffer.writeInt(999);
                // Flags = 0xC0 (Vendor + Mandatory)
                buffer.writeByte(0xC0);
                // Length = 16 (12 header + 4 data)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x10);
                // Vendor-ID = 10415
                buffer.writeInt(10415);
                // Data = 0x00000001
                buffer.writeInt(1);

                Optional<Avp> result = decoder.decode(buffer);

                assertTrue(result.isPresent());
                Avp avp = result.get();
                assertEquals(999, avp.getCode());
                assertTrue(avp.isVendorSpecific());
                assertTrue(avp.isMandatory());
                assertEquals(10415, avp.getVendorId());
                assertEquals(1L, avp.asUnsigned32());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Vendor-specific AVP with padding encodes and decodes correctly")
        void vendorSpecific_withPadding_roundTrip() {
            byte[] data = new byte[]{0x01, 0x02, 0x03}; // 3 bytes -> needs 1 byte padding
            byte flags = (byte) (Avp.FLAG_VENDOR | Avp.FLAG_MANDATORY);
            int vendorId = 12345;
            Avp original = new Avp(500, flags, vendorId, data);

            ByteBuf encoded = AvpEncoder.encode(original);
            try {
                // Header(12) + data(3) = 15, padded to 16
                assertEquals(16, encoded.readableBytes());

                Optional<Avp> decoded = decoder.decode(encoded);
                assertTrue(decoded.isPresent());
                Avp avp = decoded.get();
                assertEquals(500, avp.getCode());
                assertTrue(avp.isVendorSpecific());
                assertEquals(12345, avp.getVendorId());
                assertArrayEquals(data, avp.asOctetString());
            } finally {
                encoded.release();
            }
        }
    }

    // =========================================================================
    // Error Handling Tests - Requirement 13.6
    // =========================================================================

    @Nested
    @DisplayName("Truncated Headers and Overflow Lengths")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Truncated header (fewer than 8 bytes) returns empty without exception")
        void truncatedHeader_lessThan8bytes_returnsEmpty() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // Only 4 bytes — less than minimum 8-byte AVP header
                buffer.writeInt(268);

                Optional<Avp> result = decoder.decode(buffer);

                assertFalse(result.isPresent());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Truncated header (7 bytes) returns empty without exception")
        void truncatedHeader_7bytes_returnsEmpty() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                buffer.writeBytes(new byte[]{0x00, 0x00, 0x01, 0x0C, 0x40, 0x00, 0x00});

                Optional<Avp> result = decoder.decode(buffer);

                assertFalse(result.isPresent());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("AVP Length exceeding available data returns empty without exception")
        void overflowLength_returnsEmpty() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code = 268
                buffer.writeInt(268);
                // Flags = 0x40
                buffer.writeByte(0x40);
                // Length = 100 (claims 92 bytes of data, but we only provide 4)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x64);
                // Only 4 bytes of data (not 92)
                buffer.writeInt(2001);

                Optional<Avp> result = decoder.decode(buffer);

                assertFalse(result.isPresent());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Empty buffer returns empty without exception")
        void emptyBuffer_returnsEmpty() {
            ByteBuf buffer = Unpooled.buffer(0);
            try {
                Optional<Avp> result = decoder.decode(buffer);
                assertFalse(result.isPresent());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Vendor-specific AVP with truncated vendor ID returns empty")
        void vendorSpecific_truncatedVendorId_returnsEmpty() {
            ByteBuf buffer = Unpooled.buffer();
            try {
                // AVP Code
                buffer.writeInt(999);
                // Flags = 0x80 (Vendor flag set)
                buffer.writeByte(0x80);
                // Length = 16 (claims vendor header + 4 data)
                buffer.writeByte(0x00);
                buffer.writeByte(0x00);
                buffer.writeByte(0x10);
                // Only 2 bytes for vendor ID (need 4)
                buffer.writeByte(0x00);
                buffer.writeByte(0x01);

                Optional<Avp> result = decoder.decode(buffer);

                assertFalse(result.isPresent());
            } finally {
                buffer.release();
            }
        }

        @Test
        @DisplayName("Overflow encoding rejects AVP exceeding max length")
        void encodeOverflow_throwsException() {
            // Create data that exceeds max length for non-vendor AVP
            // Max is 2^24 - 1 - 8 = 16777207 bytes
            // We can't allocate that much, but we can test the boundary logic
            // by using a vendor-specific AVP with max = 2^24 - 1 - 12 = 16777203
            // Instead, test that the encoder validates properly with a mock scenario
            // For practical testing, verify the exception message format
            byte[] tooLargeForVendor = new byte[AvpEncoder.MAX_DATA_LENGTH_VENDOR + 1];
            byte flags = (byte) (Avp.FLAG_VENDOR | Avp.FLAG_MANDATORY);
            Avp avp = new Avp(999, flags, 10415, tooLargeForVendor);

            ByteBuf buffer = Unpooled.buffer();
            try {
                DiameterEncodingException ex = assertThrows(
                        DiameterEncodingException.class,
                        () -> AvpEncoder.encode(avp, buffer));
                assertTrue(ex.getMessage().contains("exceeds maximum"));
            } finally {
                buffer.release();
            }
        }
    }
}
