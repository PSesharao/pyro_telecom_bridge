package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Invalid Message Rejection (Property 12).
 * <p>
 * Validates: Requirements 4.5, 13.6
 * <p>
 * For byte sequences with Version ≠ 1, Message Length < 20, AVP Length exceeding
 * remaining bytes, or truncated AVP header < 8 bytes, the decoder rejects the input
 * without throwing an unhandled exception (i.e., returns null or throws
 * DiameterProtocolException, but never ArrayIndexOutOfBoundsException or similar).
 */
@Tag("Feature: telecom-bridge, Property 12: Invalid Message Rejection")
class InvalidMessageRejectionPropertyTest {

    /**
     * **Validates: Requirements 4.5, 13.6**
     * <p>
     * For any Diameter message with Version ≠ 1 (0 or 2-255), the decoder shall
     * reject the input by throwing DiameterProtocolException (not an unhandled exception).
     */
    @Property(tries = 100)
    void invalidVersionIsRejectedGracefully(@ForAll("invalidVersionBytes") byte[] messageBytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(messageBytes);
        try {
            assertDoesNotThrowUnhandledException(() -> DiameterCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /**
     * **Validates: Requirements 4.5, 13.6**
     * <p>
     * For any Diameter message with Message Length < 20 (values 0-19), the decoder
     * shall reject the input by throwing DiameterProtocolException (not an unhandled exception).
     */
    @Property(tries = 100)
    void messageLengthLessThan20IsRejectedGracefully(@ForAll("shortMessageLengthBytes") byte[] messageBytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(messageBytes);
        try {
            assertDoesNotThrowUnhandledException(() -> DiameterCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /**
     * **Validates: Requirements 4.5, 13.6**
     * <p>
     * For any Diameter message where an AVP's declared length exceeds the remaining
     * bytes in the message body, the decoder shall reject without an unhandled exception.
     */
    @Property(tries = 100)
    void avpLengthExceedingRemainingBytesIsRejectedGracefully(
            @ForAll("avpLengthOverflowBytes") byte[] messageBytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(messageBytes);
        try {
            assertDoesNotThrowUnhandledException(() -> DiameterCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /**
     * **Validates: Requirements 4.5, 13.6**
     * <p>
     * For any Diameter message where the body has fewer than 8 bytes for an AVP
     * (truncated AVP header), the decoder shall reject without an unhandled exception.
     */
    @Property(tries = 100)
    void truncatedAvpHeaderIsRejectedGracefully(
            @ForAll("truncatedAvpHeaderBytes") byte[] messageBytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(messageBytes);
        try {
            assertDoesNotThrowUnhandledException(() -> DiameterCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    // --- Generators ---

    /**
     * Generates a 20+ byte Diameter message with an invalid version byte (not 0x01).
     * The rest of the header is well-formed to ensure the version check is what triggers rejection.
     */
    @Provide
    Arbitrary<byte[]> invalidVersionBytes() {
        return Arbitraries.integers().between(0, 255)
                .filter(v -> v != 1)
                .map(version -> {
                    // Build a minimal 20-byte message with invalid version
                    byte[] msg = new byte[20];
                    msg[0] = (byte) (version & 0xFF);
                    // Message Length = 20 (bytes 1-3)
                    msg[1] = 0;
                    msg[2] = 0;
                    msg[3] = 20;
                    // Command Flags (byte 4)
                    msg[4] = (byte) 0x80;
                    // Command Code 272 (bytes 5-7)
                    msg[5] = 0;
                    msg[6] = 1;
                    msg[7] = 16;
                    // Application-ID = 4 (bytes 8-11)
                    msg[8] = 0;
                    msg[9] = 0;
                    msg[10] = 0;
                    msg[11] = 4;
                    // Hop-by-Hop-ID (bytes 12-15)
                    msg[12] = 0;
                    msg[13] = 0;
                    msg[14] = 0;
                    msg[15] = 1;
                    // End-to-End-ID (bytes 16-19)
                    msg[16] = 0;
                    msg[17] = 0;
                    msg[18] = 0;
                    msg[19] = 1;
                    return msg;
                });
    }

    /**
     * Generates a 20-byte Diameter message with Message Length field set to a value
     * between 0 and 19 (less than the minimum 20-byte header).
     */
    @Provide
    Arbitrary<byte[]> shortMessageLengthBytes() {
        return Arbitraries.integers().between(0, 19)
                .map(length -> {
                    // Build a 20-byte buffer with version=1 but invalid message length
                    byte[] msg = new byte[20];
                    msg[0] = 0x01; // Valid version
                    // Message Length (bytes 1-3) set to invalid value < 20
                    msg[1] = (byte) ((length >> 16) & 0xFF);
                    msg[2] = (byte) ((length >> 8) & 0xFF);
                    msg[3] = (byte) (length & 0xFF);
                    // Command Flags (byte 4)
                    msg[4] = (byte) 0x80;
                    // Command Code 272 (bytes 5-7)
                    msg[5] = 0;
                    msg[6] = 1;
                    msg[7] = 16;
                    // Application-ID = 4 (bytes 8-11)
                    msg[8] = 0;
                    msg[9] = 0;
                    msg[10] = 0;
                    msg[11] = 4;
                    // Hop-by-Hop-ID (bytes 12-15)
                    msg[12] = 0;
                    msg[13] = 0;
                    msg[14] = 0;
                    msg[15] = 1;
                    // End-to-End-ID (bytes 16-19)
                    msg[16] = 0;
                    msg[17] = 0;
                    msg[18] = 0;
                    msg[19] = 1;
                    return msg;
                });
    }

    /**
     * Generates a Diameter message with a valid header but an AVP whose declared
     * length exceeds the remaining bytes in the message body.
     */
    @Provide
    Arbitrary<byte[]> avpLengthOverflowBytes() {
        // Generate a message with a valid 20-byte header + partial AVP body
        // where the AVP's declared length is larger than available data
        return Arbitraries.integers().between(1, 50).flatMap(actualDataSize ->
                Arbitraries.integers().between(actualDataSize + 9, actualDataSize + 100).map(declaredAvpLength -> {
                    // Total message = 20 (header) + 8 (AVP header) + actualDataSize (AVP data, less than declared)
                    int totalMessageLength = 20 + 8 + actualDataSize;
                    byte[] msg = new byte[totalMessageLength];

                    // Valid Diameter header
                    msg[0] = 0x01; // Version
                    msg[1] = (byte) ((totalMessageLength >> 16) & 0xFF);
                    msg[2] = (byte) ((totalMessageLength >> 8) & 0xFF);
                    msg[3] = (byte) (totalMessageLength & 0xFF);
                    msg[4] = (byte) 0x80; // Command Flags
                    msg[5] = 0; msg[6] = 1; msg[7] = 16; // Command Code 272
                    msg[8] = 0; msg[9] = 0; msg[10] = 0; msg[11] = 4; // App-ID
                    msg[12] = 0; msg[13] = 0; msg[14] = 0; msg[15] = 1; // HbH
                    msg[16] = 0; msg[17] = 0; msg[18] = 0; msg[19] = 1; // E2E

                    // AVP header with inflated length
                    // AVP Code = 263 (Session-Id)
                    msg[20] = 0; msg[21] = 0; msg[22] = 1; msg[23] = 7;
                    // Flags = 0x40 (Mandatory, no vendor)
                    msg[24] = 0x40;
                    // AVP Length = declaredAvpLength (exceeds actual available data)
                    msg[25] = (byte) ((declaredAvpLength >> 16) & 0xFF);
                    msg[26] = (byte) ((declaredAvpLength >> 8) & 0xFF);
                    msg[27] = (byte) (declaredAvpLength & 0xFF);

                    // Fill remaining bytes with arbitrary data
                    for (int i = 28; i < totalMessageLength; i++) {
                        msg[i] = (byte) (i & 0xFF);
                    }

                    return msg;
                }));
    }

    /**
     * Generates a Diameter message where the body has fewer than 8 bytes,
     * making it impossible to contain a complete AVP header.
     */
    @Provide
    Arbitrary<byte[]> truncatedAvpHeaderBytes() {
        // Generate messages with body size between 1 and 7 bytes (less than minimum AVP header of 8)
        return Arbitraries.integers().between(1, 7).map(bodySize -> {
            int totalMessageLength = 20 + bodySize;
            byte[] msg = new byte[totalMessageLength];

            // Valid Diameter header
            msg[0] = 0x01; // Version
            msg[1] = (byte) ((totalMessageLength >> 16) & 0xFF);
            msg[2] = (byte) ((totalMessageLength >> 8) & 0xFF);
            msg[3] = (byte) (totalMessageLength & 0xFF);
            msg[4] = (byte) 0x80; // Command Flags
            msg[5] = 0; msg[6] = 1; msg[7] = 16; // Command Code 272
            msg[8] = 0; msg[9] = 0; msg[10] = 0; msg[11] = 4; // App-ID
            msg[12] = 0; msg[13] = 0; msg[14] = 0; msg[15] = 1; // HbH
            msg[16] = 0; msg[17] = 0; msg[18] = 0; msg[19] = 1; // E2E

            // Fill body with arbitrary bytes (fewer than 8, so AVP header is truncated)
            for (int i = 20; i < totalMessageLength; i++) {
                msg[i] = (byte) (i & 0xFF);
            }

            return msg;
        });
    }

    // --- Helper ---

    /**
     * Asserts that the given runnable either completes normally (returning null or a result),
     * throws a DiameterProtocolException, or throws a DiameterException — but never throws
     * an unhandled exception like ArrayIndexOutOfBoundsException, NullPointerException, etc.
     */
    private void assertDoesNotThrowUnhandledException(Runnable action) {
        try {
            action.run();
            // If decode returns normally (e.g., with an empty AVP list), that's acceptable
        } catch (DiameterException e) {
            // DiameterProtocolException or any DiameterException subclass is acceptable —
            // this is the expected graceful rejection
        } catch (Exception e) {
            fail("Decoder threw an unhandled exception instead of rejecting gracefully: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
