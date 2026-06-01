package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Encodes and decodes Diameter messages to/from Netty {@link ByteBuf} instances.
 * <p>
 * The codec handles the 20-byte Diameter header and delegates AVP parsing
 * to {@link AvpDecoder}.
 */
public class DiameterCodec {

    private static final Logger log = LoggerFactory.getLogger(DiameterCodec.class);

    private static final AvpDecoder avpDecoder = new AvpDecoder();

    /**
     * Encodes a complete Diameter message into a ByteBuf.
     * <p>
     * The 20-byte header is written first with a placeholder for Message Length,
     * then all AVPs are encoded in order. Finally, the total Message Length is
     * written back into bytes 1-3 of the header.
     *
     * @param message the Diameter message to encode
     * @return a ByteBuf containing the encoded message (caller must release)
     */
    public static ByteBuf encode(DiameterMessage message) {
        DiameterHeader header = message.getHeader();
        ByteBuf buffer = Unpooled.buffer();

        try {
            // Byte 0: Version (always 0x01)
            buffer.writeByte(header.version());

            // Bytes 1-3: Message Length placeholder (will be filled after encoding AVPs)
            int lengthIndex = buffer.writerIndex();
            buffer.writeByte(0);
            buffer.writeByte(0);
            buffer.writeByte(0);

            // Byte 4: Command Flags
            buffer.writeByte(header.commandFlags());

            // Bytes 5-7: Command Code (3 bytes big-endian)
            buffer.writeByte((header.commandCode() >> 16) & 0xFF);
            buffer.writeByte((header.commandCode() >> 8) & 0xFF);
            buffer.writeByte(header.commandCode() & 0xFF);

            // Bytes 8-11: Application-ID (4 bytes big-endian)
            buffer.writeInt((int) header.applicationId());

            // Bytes 12-15: Hop-by-Hop-ID (4 bytes big-endian)
            buffer.writeInt((int) header.hopByHopId());

            // Bytes 16-19: End-to-End-ID (4 bytes big-endian)
            buffer.writeInt((int) header.endToEndId());

            // Encode all AVPs in order
            for (Avp avp : message.getAvps()) {
                AvpEncoder.encode(avp, buffer);
            }

            // Calculate and set total Message Length at bytes 1-3
            int totalLength = buffer.writerIndex();
            buffer.setByte(lengthIndex, (totalLength >> 16) & 0xFF);
            buffer.setByte(lengthIndex + 1, (totalLength >> 8) & 0xFF);
            buffer.setByte(lengthIndex + 2, totalLength & 0xFF);

            return buffer;
        } catch (Exception e) {
            buffer.release();
            throw e;
        }
    }

    /**
     * Decodes a Diameter message from the given buffer.
     * <p>
     * Parses the 20-byte header, validates the version and message length,
     * then decodes AVPs from the message body.
     *
     * @param buffer the ByteBuf containing a complete Diameter message
     * @return the decoded DiameterMessage
     * @throws DiameterProtocolException if the version is not 1, the message length
     *         is less than 20, or the buffer has fewer bytes than the declared message length
     */
    public static DiameterMessage decode(ByteBuf buffer) {
        // Validate minimum readable bytes for header
        if (buffer.readableBytes() < DiameterHeader.HEADER_SIZE) {
            log.error("Diameter decode failed: buffer has fewer than 20 bytes (readable={})", buffer.readableBytes());
            throw new DiameterProtocolException(
                    "Buffer has fewer than 20 bytes for Diameter header: readable=" + buffer.readableBytes());
        }

        // Byte 0: Version
        byte version = buffer.readByte();
        if (version != DiameterHeader.DIAMETER_VERSION) {
            log.error("Diameter decode failed: invalid version 0x{}, expected 0x01",
                    String.format("%02X", version & 0xFF));
            throw new DiameterProtocolException(
                    "Invalid Diameter version: 0x" + String.format("%02X", version & 0xFF) + ", expected 0x01");
        }

        // Bytes 1-3: Message Length (3 bytes, big-endian)
        int messageLength = ((buffer.readByte() & 0xFF) << 16)
                | ((buffer.readByte() & 0xFF) << 8)
                | (buffer.readByte() & 0xFF);

        if (messageLength < DiameterHeader.HEADER_SIZE) {
            log.error("Diameter decode failed: message length {} is less than minimum 20", messageLength);
            throw new DiameterProtocolException(
                    "Invalid Diameter message length: " + messageLength + ", minimum is 20");
        }

        // Check that the buffer has enough bytes for the full message
        // We've already read 4 bytes (version + 3 length bytes), so we need (messageLength - 4) more
        int remainingMessageBytes = messageLength - 4;
        if (buffer.readableBytes() < remainingMessageBytes) {
            log.error("Diameter decode failed: buffer has {} readable bytes but message requires {} more bytes after header prefix",
                    buffer.readableBytes(), remainingMessageBytes);
            throw new DiameterProtocolException(
                    "Buffer underflow: need " + remainingMessageBytes + " more bytes but only " + buffer.readableBytes() + " available");
        }

        // Byte 4: Command Flags
        byte commandFlags = buffer.readByte();

        // Bytes 5-7: Command Code (3 bytes, big-endian)
        int commandCode = ((buffer.readByte() & 0xFF) << 16)
                | ((buffer.readByte() & 0xFF) << 8)
                | (buffer.readByte() & 0xFF);

        // Bytes 8-11: Application-ID (4 bytes, big-endian, unsigned int → long)
        long applicationId = buffer.readInt() & 0xFFFFFFFFL;

        // Bytes 12-15: Hop-by-Hop-ID (4 bytes, big-endian, unsigned int → long)
        long hopByHopId = buffer.readInt() & 0xFFFFFFFFL;

        // Bytes 16-19: End-to-End-ID (4 bytes, big-endian, unsigned int → long)
        long endToEndId = buffer.readInt() & 0xFFFFFFFFL;

        // Build the header
        DiameterHeader header = new DiameterHeader(
                version, messageLength, commandFlags, commandCode,
                applicationId, hopByHopId, endToEndId
        );

        // Parse AVPs from remaining message body (messageLength - 20 bytes)
        int avpBodyLength = messageLength - DiameterHeader.HEADER_SIZE;
        List<Avp> avps = avpDecoder.decodeAll(buffer, avpBodyLength);

        return new DiameterMessage(header, avps);
    }
}
