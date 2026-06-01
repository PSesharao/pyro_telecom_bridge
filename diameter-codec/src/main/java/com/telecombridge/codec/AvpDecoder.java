package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Decodes Diameter AVPs from a Netty {@link ByteBuf}.
 * <p>
 * AVP wire format (RFC 6733 Section 4):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           AVP Code                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V M P r r r r r|                  AVP Length                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Vendor-ID (opt)                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |    Data ...
 * +-+-+-+-+-+-+-+-+
 * </pre>
 * <p>
 * Error handling: truncated headers (&lt; 8 bytes) or AVP Length exceeding
 * remaining data are silently rejected (return empty Optional or skip the AVP)
 * without throwing exceptions or logging, per Requirement 13.6.
 */
public class AvpDecoder {

    /** Minimum AVP header size (without vendor ID). */
    private static final int MIN_HEADER_SIZE = Avp.HEADER_SIZE;

    /** AVP header size with vendor ID. */
    private static final int VENDOR_HEADER_SIZE = Avp.HEADER_SIZE_VENDOR;

    /** Vendor-Specific flag bit mask. */
    private static final int VENDOR_FLAG = 0x80;

    /**
     * Decodes a single AVP from the current reader position of the buffer.
     * <p>
     * On success, the buffer's reader index is advanced past the AVP data
     * and any padding bytes. On failure (truncated header, invalid length),
     * the buffer's reader index is not modified and an empty Optional is returned.
     *
     * @param buffer the ByteBuf to read from
     * @return an Optional containing the decoded AVP, or empty if the data is
     *         truncated or invalid
     */
    public Optional<Avp> decode(ByteBuf buffer) {
        // Check minimum header bytes available
        if (buffer.readableBytes() < MIN_HEADER_SIZE) {
            return Optional.empty();
        }

        // Mark the reader index so we can reset on failure
        buffer.markReaderIndex();

        // Read AVP Code (4 bytes, big-endian)
        int code = buffer.readInt();

        // Read Flags (1 byte)
        byte flags = buffer.readByte();

        // Read Length (3 bytes, big-endian)
        int avpLength = ((buffer.readByte() & 0xFF) << 16)
                | ((buffer.readByte() & 0xFF) << 8)
                | (buffer.readByte() & 0xFF);

        // Determine header size based on Vendor flag
        boolean vendorSpecific = (flags & VENDOR_FLAG) != 0;
        int headerSize = vendorSpecific ? VENDOR_HEADER_SIZE : MIN_HEADER_SIZE;

        // Validate AVP Length is at least the header size
        if (avpLength < headerSize) {
            buffer.resetReaderIndex();
            return Optional.empty();
        }

        // If vendor-specific, we need 4 more bytes for Vendor-ID
        if (vendorSpecific && buffer.readableBytes() < 4) {
            buffer.resetReaderIndex();
            return Optional.empty();
        }

        // Read Vendor-ID if present
        int vendorId = 0;
        if (vendorSpecific) {
            vendorId = buffer.readInt();
        }

        // Calculate data length
        int dataLength = avpLength - headerSize;

        // Validate that enough data bytes are available (data + padding)
        if (buffer.readableBytes() < dataLength) {
            buffer.resetReaderIndex();
            return Optional.empty();
        }

        // Read data bytes
        byte[] data = new byte[dataLength];
        buffer.readBytes(data);

        // Skip padding to 4-byte boundary
        int padding = (4 - (avpLength % 4)) % 4;
        if (buffer.readableBytes() >= padding) {
            buffer.skipBytes(padding);
        }

        return Optional.of(new Avp(code, flags, vendorId, data));
    }

    /**
     * Decodes all AVPs within the specified length from the buffer's current
     * reader position.
     * <p>
     * Reads AVPs sequentially until the specified number of bytes have been
     * consumed or a decoding error occurs (in which case remaining bytes are
     * skipped silently).
     *
     * @param buffer the ByteBuf to read from
     * @param length the number of bytes to decode AVPs from
     * @return a list of decoded AVPs (may be empty if no valid AVPs found)
     */
    public List<Avp> decodeAll(ByteBuf buffer, int length) {
        if (length <= 0 || buffer.readableBytes() < length) {
            return Collections.emptyList();
        }

        List<Avp> avps = new ArrayList<>();
        int startIndex = buffer.readerIndex();
        int endIndex = startIndex + length;

        while (buffer.readerIndex() < endIndex) {
            int remaining = endIndex - buffer.readerIndex();

            // Not enough bytes for a minimal AVP header
            if (remaining < MIN_HEADER_SIZE) {
                // Skip remaining bytes silently
                buffer.skipBytes(remaining);
                break;
            }

            Optional<Avp> avpOpt = decode(buffer);

            if (avpOpt.isPresent()) {
                avps.add(avpOpt.get());
            } else {
                // Decoding failed — skip remaining bytes in this block silently
                int leftover = endIndex - buffer.readerIndex();
                if (leftover > 0) {
                    buffer.skipBytes(leftover);
                }
                break;
            }
        }

        return avps;
    }
}
