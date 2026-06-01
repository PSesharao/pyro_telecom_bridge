package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Diameter Byte Decode/Encode Round-Trip (Property 9).
 * <p>
 * **Validates: Requirements 4.7**
 * <p>
 * For any valid Diameter byte sequence, decoding then encoding produces
 * byte-for-byte identical output.
 * <p>
 * Strategy: Generate valid DiameterMessages, encode them to get valid byte sequences,
 * then verify decode→encode produces identical bytes.
 */
@Tag("Feature: telecom-bridge, Property 9: Diameter Byte Decode/Encode Round-Trip")
class DiameterByteRoundTripPropertyTest {

    /**
     * **Validates: Requirements 4.7**
     * <p>
     * For any valid Diameter byte sequence (produced by encoding a valid DiameterMessage),
     * decoding then re-encoding produces byte-for-byte identical output.
     */
    @Property(tries = 100)
    void byteDecodeEncodeRoundTrip(@ForAll("validDiameterMessages") DiameterMessage originalMessage) {
        // Step 1: Encode the message to get a valid byte sequence
        ByteBuf originalBytes = DiameterCodec.encode(originalMessage);
        try {
            // Capture the original bytes for comparison
            byte[] originalByteArray = new byte[originalBytes.readableBytes()];
            originalBytes.getBytes(originalBytes.readerIndex(), originalByteArray);

            // Step 2: Decode the byte sequence back to a DiameterMessage
            DiameterMessage decoded = DiameterCodec.decode(originalBytes.duplicate());

            // Step 3: Re-encode the decoded message
            ByteBuf reEncodedBytes = DiameterCodec.encode(decoded);
            try {
                // Step 4: Compare byte-for-byte
                byte[] reEncodedByteArray = new byte[reEncodedBytes.readableBytes()];
                reEncodedBytes.getBytes(reEncodedBytes.readerIndex(), reEncodedByteArray);

                assertArrayEquals(originalByteArray, reEncodedByteArray,
                        "Byte decode/encode round-trip failed: decoded then re-encoded bytes differ from original. "
                                + "Original length=" + originalByteArray.length
                                + ", re-encoded length=" + reEncodedByteArray.length);
            } finally {
                reEncodedBytes.release();
            }
        } finally {
            originalBytes.release();
        }
    }

    /**
     * Generates valid DiameterMessage instances with random headers and 1-20 AVPs of mixed types.
     */
    @Provide
    Arbitrary<DiameterMessage> validDiameterMessages() {
        Arbitrary<DiameterHeader> headerArb = validHeaders();
        Arbitrary<List<Avp>> avpsArb = validAvpList();

        return Combinators.combine(headerArb, avpsArb).as((header, avps) -> {
            DiameterMessage message = new DiameterMessage(header, avps);
            return message;
        });
    }

    /**
     * Generates valid Diameter headers with version 0x01 and valid command codes.
     * Message length is set to 0 as it will be calculated during encoding.
     */
    private Arbitrary<DiameterHeader> validHeaders() {
        Arbitrary<Byte> commandFlags = Arbitraries.of(
                (byte) 0x80,        // Request only
                (byte) 0xC0,        // Request + Proxiable
                (byte) 0x40,        // Proxiable only (answer)
                (byte) 0x00         // Plain answer
        );
        Arbitrary<Integer> commandCodes = Arbitraries.of(272, 257, 280, 265, 274, 275);
        Arbitrary<Long> applicationIds = Arbitraries.longs().between(0L, 0xFFFFFFFFL);
        Arbitrary<Long> hopByHopIds = Arbitraries.longs().between(0L, 0xFFFFFFFFL);
        Arbitrary<Long> endToEndIds = Arbitraries.longs().between(0L, 0xFFFFFFFFL);

        return Combinators.combine(commandFlags, commandCodes, applicationIds, hopByHopIds, endToEndIds)
                .as((flags, code, appId, hbhId, e2eId) ->
                        new DiameterHeader(
                                DiameterHeader.DIAMETER_VERSION,
                                0, // placeholder, will be calculated during encoding
                                flags,
                                code,
                                appId,
                                hbhId,
                                e2eId
                        ));
    }

    /**
     * Generates a list of 1-20 valid AVPs with mixed types.
     */
    private Arbitrary<List<Avp>> validAvpList() {
        return validAvp().list().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Generates a single valid AVP with random code, flags, and data.
     * Generates both vendor-specific and non-vendor-specific AVPs.
     */
    private Arbitrary<Avp> validAvp() {
        // Non-vendor-specific AVPs
        Arbitrary<Avp> nonVendorAvp = Combinators.combine(
                Arbitraries.integers().between(1, 1000),
                Arbitraries.of(Avp.FLAG_MANDATORY, (byte) 0x00),
                Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(128)
        ).as((code, flags, data) -> new Avp(code, flags, 0, data));

        // Vendor-specific AVPs
        Arbitrary<Avp> vendorAvp = Combinators.combine(
                Arbitraries.integers().between(1, 1000),
                Arbitraries.of((byte) (Avp.FLAG_VENDOR | Avp.FLAG_MANDATORY), Avp.FLAG_VENDOR),
                Arbitraries.integers().between(1, 99999),
                Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(128)
        ).as((code, flags, vendorId, data) -> new Avp(code, flags, vendorId, data));

        // Mix of vendor and non-vendor AVPs (80% non-vendor, 20% vendor)
        return Arbitraries.frequencyOf(
                Tuple.of(4, nonVendorAvp),
                Tuple.of(1, vendorAvp)
        );
    }
}
