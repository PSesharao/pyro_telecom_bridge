package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Netty {@link ByteToMessageDecoder} that frames Diameter messages from a TCP byte stream.
 * <p>
 * Diameter messages are length-prefixed: bytes 1-3 of the header contain the total
 * Message Length (3 bytes, big-endian). This decoder buffers incoming bytes until a
 * complete message is available, then passes the framed message to
 * {@link DiameterCodec#decode(ByteBuf)} for full parsing.
 * <p>
 * The decoder handles:
 * <ul>
 *   <li>Partial reads — returns without consuming bytes if insufficient data</li>
 *   <li>Multiple messages in a single buffer — the {@link ByteToMessageDecoder} loop
 *       calls {@code decode()} repeatedly until no more complete messages are available</li>
 *   <li>Invalid messages — catches {@link DiameterProtocolException}, logs the error,
 *       and skips the malformed bytes to avoid blocking the pipeline</li>
 * </ul>
 */
public class MessageFrameDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(MessageFrameDecoder.class);

    /**
     * Minimum bytes needed to read the message length: 1 byte version + 3 bytes length.
     */
    private static final int LENGTH_FIELD_END_OFFSET = 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Step 1: Check if at least 4 bytes are available (version + 3-byte length)
        if (in.readableBytes() < LENGTH_FIELD_END_OFFSET) {
            return;
        }

        // Step 2: Mark the reader index so we can reset if not enough data
        in.markReaderIndex();

        // Step 3: Read byte 0 (version) — skip it for framing purposes
        in.readByte();

        // Step 4: Read bytes 1-3 as the Message Length (3 bytes big-endian)
        int messageLength = ((in.readByte() & 0xFF) << 16)
                | ((in.readByte() & 0xFF) << 8)
                | (in.readByte() & 0xFF);

        // Step 5: Reset the reader index to the start of the message
        in.resetReaderIndex();

        // Validate message length
        if (messageLength < DiameterHeader.HEADER_SIZE) {
            log.error("Invalid Diameter message length: {}, minimum is {}. Skipping 4 bytes.",
                    messageLength, DiameterHeader.HEADER_SIZE);
            // Skip the 4 bytes we peeked at to avoid infinite loop
            in.skipBytes(LENGTH_FIELD_END_OFFSET);
            return;
        }

        // Step 6: Check if the full message (messageLength bytes) is available
        if (in.readableBytes() < messageLength) {
            // Not enough bytes yet — wait for more data
            return;
        }

        // Step 7: Read exactly messageLength bytes into a new ByteBuf slice
        ByteBuf messageFrame = in.readRetainedSlice(messageLength);

        try {
            // Step 8: Decode the message using DiameterCodec and add to output list
            DiameterMessage message = DiameterCodec.decode(messageFrame);
            out.add(message);
        } catch (DiameterProtocolException e) {
            log.error("Failed to decode Diameter message: {}", e.getMessage());
            // Skip the bytes — they've already been consumed by readRetainedSlice
        } finally {
            // Release the slice buffer
            messageFrame.release();
        }
    }
}
