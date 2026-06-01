package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for Message Framing Correctness (Property 11).
 * <p>
 * **Validates: Requirements 4.1**
 * <p>
 * For any sequence of N valid Diameter messages concatenated into a single byte stream
 * (possibly delivered in arbitrary chunk sizes), the frame decoder SHALL produce exactly
 * N complete messages, each with correct content matching the original messages.
 */
@Tag("Feature: telecom-bridge, Property 11: Message Framing Correctness")
class MessageFramingPropertyTest {

    /**
     * **Validates: Requirements 4.1**
     * <p>
     * For N valid messages concatenated into a byte stream delivered in arbitrary chunk sizes,
     * frame decoder produces exactly N complete correct messages.
     */
    @Property(tries = 100)
    void framingProducesExactlyNCorrectMessages(
            @ForAll("validDiameterMessages") List<DiameterMessage> originalMessages,
            @ForAll("chunkStrategy") ChunkStrategy strategy) {

        // Step 1: Encode all messages and concatenate into a single byte stream
        ByteBuf concatenated = Unpooled.buffer();
        List<byte[]> encodedMessages = new ArrayList<>();

        try {
            for (DiameterMessage msg : originalMessages) {
                ByteBuf encoded = DiameterCodec.encode(msg);
                byte[] bytes = new byte[encoded.readableBytes()];
                encoded.readBytes(bytes);
                encodedMessages.add(bytes);
                concatenated.writeBytes(bytes);
                encoded.release();
            }

            byte[] fullStream = new byte[concatenated.readableBytes()];
            concatenated.readBytes(fullStream);

            // Step 2: Create an EmbeddedChannel with the MessageFrameDecoder
            EmbeddedChannel channel = new EmbeddedChannel(new MessageFrameDecoder());

            // Step 3: Feed the stream in arbitrary chunk sizes
            List<Integer> chunkSizes = strategy.computeChunks(fullStream.length);
            int offset = 0;
            for (int chunkSize : chunkSizes) {
                int actualChunk = Math.min(chunkSize, fullStream.length - offset);
                if (actualChunk <= 0) break;
                ByteBuf chunk = Unpooled.wrappedBuffer(fullStream, offset, actualChunk);
                channel.writeInbound(chunk);
                offset += actualChunk;
            }

            // Step 4: Collect all decoded messages from the channel
            List<DiameterMessage> decodedMessages = new ArrayList<>();
            Object decoded;
            while ((decoded = channel.readInbound()) != null) {
                assertTrue(decoded instanceof DiameterMessage,
                        "Expected DiameterMessage but got " + decoded.getClass().getSimpleName());
                decodedMessages.add((DiameterMessage) decoded);
            }

            channel.finish();

            // Step 5: Verify exactly N messages were produced
            assertEquals(originalMessages.size(), decodedMessages.size(),
                    "Expected " + originalMessages.size() + " messages but got " + decodedMessages.size());

            // Step 6: Verify each decoded message matches the original
            for (int i = 0; i < originalMessages.size(); i++) {
                DiameterMessage original = originalMessages.get(i);
                DiameterMessage decodedMsg = decodedMessages.get(i);

                // Verify header fields
                assertEquals(original.getHeader().version(), decodedMsg.getHeader().version(),
                        "Message[" + i + "] version mismatch");
                assertEquals(original.getHeader().commandFlags(), decodedMsg.getHeader().commandFlags(),
                        "Message[" + i + "] commandFlags mismatch");
                assertEquals(original.getHeader().commandCode(), decodedMsg.getHeader().commandCode(),
                        "Message[" + i + "] commandCode mismatch");
                assertEquals(original.getHeader().applicationId(), decodedMsg.getHeader().applicationId(),
                        "Message[" + i + "] applicationId mismatch");
                assertEquals(original.getHeader().hopByHopId(), decodedMsg.getHeader().hopByHopId(),
                        "Message[" + i + "] hopByHopId mismatch");
                assertEquals(original.getHeader().endToEndId(), decodedMsg.getHeader().endToEndId(),
                        "Message[" + i + "] endToEndId mismatch");

                // Verify AVP count
                assertEquals(original.getAvps().size(), decodedMsg.getAvps().size(),
                        "Message[" + i + "] AVP count mismatch");

                // Verify each AVP
                for (int j = 0; j < original.getAvps().size(); j++) {
                    Avp originalAvp = original.getAvps().get(j);
                    Avp decodedAvp = decodedMsg.getAvps().get(j);

                    assertEquals(originalAvp.getCode(), decodedAvp.getCode(),
                            "Message[" + i + "] AVP[" + j + "] code mismatch");
                    assertEquals(originalAvp.getFlags(), decodedAvp.getFlags(),
                            "Message[" + i + "] AVP[" + j + "] flags mismatch");
                    assertEquals(originalAvp.getVendorId(), decodedAvp.getVendorId(),
                            "Message[" + i + "] AVP[" + j + "] vendorId mismatch");
                    assertArrayEquals(originalAvp.getData(), decodedAvp.getData(),
                            "Message[" + i + "] AVP[" + j + "] data mismatch");
                }
            }
        } finally {
            concatenated.release();
        }
    }

    // ========================================================================
    // Generators
    // ========================================================================

    @Provide
    Arbitrary<List<DiameterMessage>> validDiameterMessages() {
        return validDiameterMessage().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<DiameterMessage> validDiameterMessage() {
        Arbitrary<Integer> commandCodeArb = Arbitraries.of(272, 257, 280);
        Arbitrary<Byte> commandFlagsArb = Arbitraries.of(
                (byte) 0xC0, // Request + Proxiable
                (byte) 0x40, // Proxiable (answer)
                (byte) 0x80, // Request only
                (byte) 0x00  // Answer only
        );
        Arbitrary<Long> applicationIdArb = Arbitraries.of(0L, 4L);
        Arbitrary<Long> hopByHopIdArb = Arbitraries.longs().between(0L, 0xFFFFFFFFL);
        Arbitrary<Long> endToEndIdArb = Arbitraries.longs().between(0L, 0xFFFFFFFFL);
        Arbitrary<List<Avp>> avpsArb = validAvp().list().ofMinSize(1).ofMaxSize(8);

        return Combinators.combine(commandCodeArb, commandFlagsArb, applicationIdArb,
                        hopByHopIdArb, endToEndIdArb, avpsArb)
                .as((commandCode, commandFlags, applicationId, hopByHopId, endToEndId, avps) -> {
                    // Message length will be calculated during encoding, use placeholder 0
                    DiameterHeader header = new DiameterHeader(
                            DiameterHeader.DIAMETER_VERSION,
                            0, // placeholder, will be set by encoder
                            commandFlags,
                            commandCode,
                            applicationId,
                            hopByHopId,
                            endToEndId
                    );
                    return new DiameterMessage(header, avps);
                });
    }

    private Arbitrary<Avp> validAvp() {
        Arbitrary<Integer> codeArb = Arbitraries.integers().between(1, 1000);
        Arbitrary<byte[]> dataArb = Arbitraries.integers().between(0, 128)
                .flatMap(len -> Arbitraries.bytes().array(byte[].class).ofSize(len));

        return Combinators.combine(codeArb, dataArb)
                .as((code, data) -> new Avp(code, Avp.FLAG_MANDATORY, data));
    }

    @Provide
    Arbitrary<ChunkStrategy> chunkStrategy() {
        return Arbitraries.of(
                ChunkStrategy.SINGLE_BYTE,
                ChunkStrategy.RANDOM_SMALL,
                ChunkStrategy.RANDOM_MEDIUM,
                ChunkStrategy.ALL_AT_ONCE
        );
    }

    // ========================================================================
    // Chunk strategy helper
    // ========================================================================

    enum ChunkStrategy {
        /** Feed one byte at a time */
        SINGLE_BYTE {
            @Override
            List<Integer> computeChunks(int totalLength) {
                List<Integer> chunks = new ArrayList<>();
                for (int i = 0; i < totalLength; i++) {
                    chunks.add(1);
                }
                return chunks;
            }
        },
        /** Random small chunks (1-10 bytes) */
        RANDOM_SMALL {
            @Override
            List<Integer> computeChunks(int totalLength) {
                return randomChunks(totalLength, 1, 10);
            }
        },
        /** Random medium chunks (1 to totalLength) */
        RANDOM_MEDIUM {
            @Override
            List<Integer> computeChunks(int totalLength) {
                return randomChunks(totalLength, 1, Math.max(1, totalLength));
            }
        },
        /** Feed all bytes at once */
        ALL_AT_ONCE {
            @Override
            List<Integer> computeChunks(int totalLength) {
                return List.of(totalLength);
            }
        };

        abstract List<Integer> computeChunks(int totalLength);

        static List<Integer> randomChunks(int totalLength, int minChunk, int maxChunk) {
            List<Integer> chunks = new ArrayList<>();
            java.util.Random rng = new java.util.Random();
            int remaining = totalLength;
            while (remaining > 0) {
                int chunkSize = minChunk + rng.nextInt(Math.max(1, maxChunk - minChunk + 1));
                chunkSize = Math.min(chunkSize, remaining);
                chunks.add(chunkSize);
                remaining -= chunkSize;
            }
            return chunks;
        }
    }
}
