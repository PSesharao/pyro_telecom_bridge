package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for AVP type-specific encode/decode round-trip.
 * <p>
 * Validates: Requirements 13.5
 */
@Tag("Feature: telecom-bridge, Property 10: AVP Type-Specific Encode/Decode Round-Trip")
class AvpTypeRoundTripPropertyTest {

    private static final int TEST_AVP_CODE = 999;
    private static final byte FLAGS_MANDATORY = Avp.FLAG_MANDATORY;

    // ========================================================================
    // Property: OctetString round-trip
    // ========================================================================

    @Property(tries = 100)
    void octetStringRoundTrip(@ForAll("octetStringData") byte[] originalData) {
        // Encode
        Avp original = new Avp(TEST_AVP_CODE, FLAGS_MANDATORY, 0, originalData);
        ByteBuf encoded = Unpooled.buffer();
        try {
            AvpEncoder.encode(original, encoded);

            // Decode
            AvpDecoder decoder = new AvpDecoder();
            Avp decoded = decoder.decode(encoded).orElseThrow(
                    () -> new AssertionError("Failed to decode OctetString AVP"));

            // Verify round-trip
            assertEquals(TEST_AVP_CODE, decoded.getCode());
            assertArrayEquals(originalData, decoded.asOctetString(),
                    "OctetString round-trip failed for data of length " + originalData.length);
        } finally {
            encoded.release();
        }
    }

    @Provide
    Arbitrary<byte[]> octetStringData() {
        return Arbitraries.integers().between(0, 1024).flatMap(length ->
                Arbitraries.bytes().array(byte[].class).ofSize(length));
    }

    // ========================================================================
    // Property: Unsigned32 round-trip
    // ========================================================================

    @Property(tries = 100)
    void unsigned32RoundTrip(@ForAll("unsigned32Values") long originalValue) {
        // Encode
        ByteBuf encoded = Unpooled.buffer();
        try {
            AvpEncoder.encodeUnsigned32(TEST_AVP_CODE, FLAGS_MANDATORY, 0, originalValue, encoded);

            // Decode
            AvpDecoder decoder = new AvpDecoder();
            Avp decoded = decoder.decode(encoded).orElseThrow(
                    () -> new AssertionError("Failed to decode Unsigned32 AVP"));

            // Verify round-trip
            assertEquals(TEST_AVP_CODE, decoded.getCode());
            assertEquals(originalValue, decoded.asUnsigned32(),
                    "Unsigned32 round-trip failed for value " + originalValue);
        } finally {
            encoded.release();
        }
    }

    @Provide
    Arbitrary<Long> unsigned32Values() {
        // Full unsigned 32-bit range: 0 to 4294967295
        return Arbitraries.longs().between(0L, 0xFFFFFFFFL);
    }

    // ========================================================================
    // Property: UTF8String round-trip
    // ========================================================================

    @Property(tries = 100)
    void utf8StringRoundTrip(@ForAll("utf8StringValues") String originalValue) {
        // Encode
        ByteBuf encoded = Unpooled.buffer();
        try {
            AvpEncoder.encodeUtf8String(TEST_AVP_CODE, FLAGS_MANDATORY, 0, originalValue, encoded);

            // Decode
            AvpDecoder decoder = new AvpDecoder();
            Avp decoded = decoder.decode(encoded).orElseThrow(
                    () -> new AssertionError("Failed to decode UTF8String AVP"));

            // Verify round-trip
            assertEquals(TEST_AVP_CODE, decoded.getCode());
            assertEquals(originalValue, decoded.asUtf8String(),
                    "UTF8String round-trip failed for string of length " + originalValue.length());
        } finally {
            encoded.release();
        }
    }

    @Provide
    Arbitrary<String> utf8StringValues() {
        // Generate strings of 0-1024 characters using valid UTF-8 characters
        return Arbitraries.integers().between(0, 1024).flatMap(length ->
                Arbitraries.strings()
                        .ofMinLength(length)
                        .ofMaxLength(length)
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .withChars(' ', '-', '_', '.', '@', '/', ':', '+')
        );
    }

    // ========================================================================
    // Property: Grouped AVP round-trip
    // ========================================================================

    @Property(tries = 100)
    void groupedAvpRoundTrip(@ForAll("groupedAvpValues") List<Avp> nestedAvps) {
        // Encode
        ByteBuf encoded = Unpooled.buffer();
        try {
            AvpEncoder.encodeGrouped(TEST_AVP_CODE, FLAGS_MANDATORY, 0, nestedAvps, encoded);

            // Decode the outer grouped AVP
            AvpDecoder decoder = new AvpDecoder();
            Avp decoded = decoder.decode(encoded).orElseThrow(
                    () -> new AssertionError("Failed to decode Grouped AVP"));

            // Verify outer AVP
            assertEquals(TEST_AVP_CODE, decoded.getCode());

            // Parse nested AVPs from the grouped data
            List<Avp> decodedNested = decoded.asGrouped();

            // Verify same number of nested AVPs
            assertEquals(nestedAvps.size(), decodedNested.size(),
                    "Grouped AVP round-trip: nested AVP count mismatch");

            // Verify each nested AVP
            for (int i = 0; i < nestedAvps.size(); i++) {
                Avp expectedNested = nestedAvps.get(i);
                Avp actualNested = decodedNested.get(i);

                assertEquals(expectedNested.getCode(), actualNested.getCode(),
                        "Nested AVP[" + i + "] code mismatch");
                assertEquals(expectedNested.getFlags(), actualNested.getFlags(),
                        "Nested AVP[" + i + "] flags mismatch");
                assertArrayEquals(expectedNested.getData(), actualNested.getData(),
                        "Nested AVP[" + i + "] data mismatch");
            }
        } finally {
            encoded.release();
        }
    }

    @Provide
    Arbitrary<List<Avp>> groupedAvpValues() {
        // Generate 1-10 nested AVPs with random data
        Arbitrary<Avp> nestedAvpArbitrary = Combinators.combine(
                Arbitraries.integers().between(1, 1000),       // AVP code
                Arbitraries.of(FLAGS_MANDATORY, (byte) 0x00),  // flags (non-vendor for simplicity)
                Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(64) // data
        ).as((code, flags, data) -> new Avp(code, flags, 0, data));

        return nestedAvpArbitrary.list().ofMinSize(1).ofMaxSize(10);
    }
}
