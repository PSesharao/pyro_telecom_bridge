package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Diameter Message Encode/Decode Round-Trip (Property 8).
 * <p>
 * **Validates: Requirements 3.7**
 * <p>
 * For any valid DiameterMessage, encoding to bytes then decoding produces
 * identical AVP codes, values, and ordering.
 */
@Tag("Feature: telecom-bridge, Property 8: Diameter Message Encode/Decode Round-Trip")
class DiameterMessageRoundTripPropertyTest {

    /**
     * **Validates: Requirements 3.7**
     * <p>
     * For any valid DiameterMessage with 1-20 AVPs of mixed types (OctetString,
     * Unsigned32, UTF8String), encoding to bytes then decoding produces a message
     * with identical AVP codes, AVP values, and AVP ordering.
     */
    @Property(tries = 100)
    void messageEncodeDecodeRoundTrip(@ForAll("validDiameterMessages") DiameterMessage original) {
        // Encode the message to bytes
        ByteBuf encoded = DiameterCodec.encode(original);
        try {
            // Decode the bytes back to a message
            DiameterMessage decoded = DiameterCodec.decode(encoded);

            // Verify AVP count matches
            List<Avp> originalAvps = original.getAvps();
            List<Avp> decodedAvps = decoded.getAvps();

            assertEquals(originalAvps.size(), decodedAvps.size(),
                    "AVP count mismatch after round-trip");

            // Verify each AVP's code, values, and ordering
            for (int i = 0; i < originalAvps.size(); i++) {
                Avp originalAvp = originalAvps.get(i);
                Avp decodedAvp = decodedAvps.get(i);

                assertEquals(originalAvp.getCode(), decodedAvp.getCode(),
                        "AVP[" + i + "] code mismatch");
                assertEquals(originalAvp.getFlags(), decodedAvp.getFlags(),
                        "AVP[" + i + "] flags mismatch");
                assertEquals(originalAvp.getVendorId(), decodedAvp.getVendorId(),
                        "AVP[" + i + "] vendorId mismatch");
                assertArrayEquals(originalAvp.getData(), decodedAvp.getData(),
                        "AVP[" + i + "] data mismatch (code=" + originalAvp.getCode() + ")");
            }
        } finally {
            encoded.release();
        }
    }

    @Provide
    Arbitrary<DiameterMessage> validDiameterMessages() {
        // Generate a random valid DiameterMessage with 1-20 AVPs of mixed types
        Arbitrary<List<Avp>> avpListArbitrary = mixedTypeAvp().list().ofMinSize(1).ofMaxSize(20);

        // Combine header fields (max 5 params per combine call) then flatMap with AVP list
        Arbitrary<DiameterHeader> headerArbitrary = Combinators.combine(
                commandFlags(),
                commandCodes(),
                applicationIds(),
                hopByHopIds(),
                endToEndIds()
        ).as((Byte flags, Integer commandCode, Long appId, Long hbhId, Long e2eId) ->
                new DiameterHeader(
                        DiameterHeader.DIAMETER_VERSION,
                        0, // messageLength placeholder - calculated during encoding
                        flags,
                        commandCode,
                        appId,
                        hbhId,
                        e2eId
                )
        );

        return Combinators.combine(headerArbitrary, avpListArbitrary)
                .as((header, avps) -> new DiameterMessage(header, avps));
    }

    /**
     * Generates AVPs of mixed types: OctetString, Unsigned32, UTF8String.
     */
    private Arbitrary<Avp> mixedTypeAvp() {
        return Arbitraries.oneOf(
                octetStringAvp(),
                unsigned32Avp(),
                utf8StringAvp()
        );
    }

    /**
     * Generates an OctetString AVP with random data (0-256 bytes).
     */
    private Arbitrary<Avp> octetStringAvp() {
        return Combinators.combine(
                avpCodes(),
                avpFlags()
        ).flatAs((code, flags) ->
                Arbitraries.integers().between(0, 256).flatMap(length ->
                        Arbitraries.bytes().array(byte[].class).ofSize(length)
                                .map(data -> new Avp(code, flags, 0, data))
                )
        );
    }

    /**
     * Generates an Unsigned32 AVP with a random 32-bit value.
     */
    private Arbitrary<Avp> unsigned32Avp() {
        return Combinators.combine(
                avpCodes(),
                avpFlags(),
                Arbitraries.longs().between(0L, 0xFFFFFFFFL)
        ).as((code, flags, value) -> {
            byte[] data = new byte[4];
            data[0] = (byte) ((value >> 24) & 0xFF);
            data[1] = (byte) ((value >> 16) & 0xFF);
            data[2] = (byte) ((value >> 8) & 0xFF);
            data[3] = (byte) (value & 0xFF);
            return new Avp(code, flags, 0, data);
        });
    }

    /**
     * Generates a UTF8String AVP with random string content (0-128 chars).
     */
    private Arbitrary<Avp> utf8StringAvp() {
        return Combinators.combine(
                avpCodes(),
                avpFlags(),
                Arbitraries.strings()
                        .ofMinLength(0)
                        .ofMaxLength(128)
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .withChars('.', '-', '_', '@')
        ).as((code, flags, value) -> {
            byte[] data = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new Avp(code, flags, 0, data);
        });
    }

    /**
     * Generates valid AVP codes (1-999 range for testing).
     */
    private Arbitrary<Integer> avpCodes() {
        return Arbitraries.integers().between(1, 999);
    }

    /**
     * Generates AVP flags (Mandatory bit set, no Vendor flag to keep non-vendor-specific).
     */
    private Arbitrary<Byte> avpFlags() {
        return Arbitraries.of(
                Avp.FLAG_MANDATORY,
                (byte) 0x00
        );
    }

    /**
     * Generates command flags (combinations of Request and Proxiable bits).
     */
    private Arbitrary<Byte> commandFlags() {
        return Arbitraries.of(
                (byte) 0x00,
                DiameterHeader.FLAG_REQUEST,
                DiameterHeader.FLAG_PROXIABLE,
                (byte) (DiameterHeader.FLAG_REQUEST | DiameterHeader.FLAG_PROXIABLE)
        );
    }

    /**
     * Generates valid command codes (3 bytes, 0 to 0xFFFFFF).
     */
    private Arbitrary<Integer> commandCodes() {
        return Arbitraries.integers().between(0, 0xFFFFFF);
    }

    /**
     * Generates valid application IDs (unsigned 32-bit).
     */
    private Arbitrary<Long> applicationIds() {
        return Arbitraries.longs().between(0L, 0xFFFFFFFFL);
    }

    /**
     * Generates valid Hop-by-Hop IDs (unsigned 32-bit).
     */
    private Arbitrary<Long> hopByHopIds() {
        return Arbitraries.longs().between(0L, 0xFFFFFFFFL);
    }

    /**
     * Generates valid End-to-End IDs (unsigned 32-bit).
     */
    private Arbitrary<Long> endToEndIds() {
        return Arbitraries.longs().between(0L, 0xFFFFFFFFL);
    }
}
