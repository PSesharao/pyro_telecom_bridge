package com.telecombridge.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiameterCodec#decode(ByteBuf)}.
 */
class DiameterCodecDecodeTest {

    @Test
    void decode_validMessageWithNoAvps_returnsCorrectHeader() {
        // Minimal valid Diameter message: 20-byte header, no AVPs
        ByteBuf buffer = Unpooled.buffer(20);
        buffer.writeByte(0x01);           // Version
        buffer.writeMedium(20);           // Message Length (3 bytes)
        buffer.writeByte(0xC0);           // Command Flags (Request + Proxiable)
        buffer.writeMedium(272);          // Command Code (CCR)
        buffer.writeInt(4);              // Application-ID
        buffer.writeInt(0x00000001);     // Hop-by-Hop-ID
        buffer.writeInt(0x00000002);     // End-to-End-ID

        DiameterMessage msg = DiameterCodec.decode(buffer);

        assertNotNull(msg);
        assertEquals(0x01, msg.getHeader().version());
        assertEquals(20, msg.getHeader().messageLength());
        assertEquals((byte) 0xC0, msg.getHeader().commandFlags());
        assertEquals(272, msg.getHeader().commandCode());
        assertEquals(4L, msg.getHeader().applicationId());
        assertEquals(1L, msg.getHeader().hopByHopId());
        assertEquals(2L, msg.getHeader().endToEndId());
        assertTrue(msg.getAvps().isEmpty());
        assertTrue(msg.isRequest());
        assertTrue(msg.isProxiable());
    }

    @Test
    void decode_validMessageWithAvp_parsesAvpCorrectly() {
        // 20-byte header + 12-byte AVP (8 header + 4 data, no padding needed)
        ByteBuf buffer = Unpooled.buffer(32);
        buffer.writeByte(0x01);           // Version
        buffer.writeMedium(32);           // Message Length
        buffer.writeByte(0x80);           // Command Flags (Request only)
        buffer.writeMedium(257);          // Command Code (CER)
        buffer.writeInt(0);              // Application-ID (Common)
        buffer.writeInt(100);            // Hop-by-Hop-ID
        buffer.writeInt(200);            // End-to-End-ID

        // AVP: Result-Code (268), Mandatory flag, length=12, value=2001
        buffer.writeInt(268);            // AVP Code
        buffer.writeByte(0x40);          // Flags (Mandatory)
        buffer.writeMedium(12);          // AVP Length (8 header + 4 data)
        buffer.writeInt(2001);           // Value (Unsigned32)

        DiameterMessage msg = DiameterCodec.decode(buffer);

        assertNotNull(msg);
        assertEquals(32, msg.getHeader().messageLength());
        assertEquals(257, msg.getHeader().commandCode());
        assertEquals(1, msg.getAvps().size());

        Avp avp = msg.getAvps().get(0);
        assertEquals(268, avp.getCode());
        assertEquals(0x40, avp.getFlags());
        assertEquals(2001L, avp.asUnsigned32());
    }

    @Test
    void decode_invalidVersion_throwsException() {
        ByteBuf buffer = Unpooled.buffer(20);
        buffer.writeByte(0x02);           // Invalid version
        buffer.writeMedium(20);
        buffer.writeByte(0x00);
        buffer.writeMedium(272);
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(2);

        DiameterProtocolException ex = assertThrows(DiameterProtocolException.class,
                () -> DiameterCodec.decode(buffer));
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    void decode_messageLengthTooShort_throwsException() {
        ByteBuf buffer = Unpooled.buffer(20);
        buffer.writeByte(0x01);           // Valid version
        buffer.writeMedium(19);           // Invalid: less than 20
        buffer.writeByte(0x00);
        buffer.writeMedium(272);
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(2);

        DiameterProtocolException ex = assertThrows(DiameterProtocolException.class,
                () -> DiameterCodec.decode(buffer));
        assertTrue(ex.getMessage().contains("length"));
    }

    @Test
    void decode_bufferTooSmallForDeclaredLength_throwsException() {
        // Declare message length of 40 but only provide 20 bytes
        ByteBuf buffer = Unpooled.buffer(20);
        buffer.writeByte(0x01);           // Version
        buffer.writeMedium(40);           // Message Length says 40
        buffer.writeByte(0x00);
        buffer.writeMedium(272);
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(2);
        // Only 20 bytes total, but message says 40

        DiameterProtocolException ex = assertThrows(DiameterProtocolException.class,
                () -> DiameterCodec.decode(buffer));
        assertTrue(ex.getMessage().contains("underflow") || ex.getMessage().contains("bytes"));
    }

    @Test
    void decode_unsignedFieldsHandledCorrectly() {
        // Test with large unsigned values (high bit set)
        ByteBuf buffer = Unpooled.buffer(20);
        buffer.writeByte(0x01);
        buffer.writeMedium(20);
        buffer.writeByte(0x00);
        buffer.writeMedium(280);          // DWR
        buffer.writeInt(0xFFFFFFFF);     // Application-ID = max unsigned 32-bit
        buffer.writeInt(0x80000001);     // Hop-by-Hop-ID with high bit set
        buffer.writeInt(0xDEADBEEF);     // End-to-End-ID

        DiameterMessage msg = DiameterCodec.decode(buffer);

        assertEquals(0xFFFFFFFFL, msg.getHeader().applicationId());
        assertEquals(0x80000001L, msg.getHeader().hopByHopId());
        assertEquals(0xDEADBEEFL, msg.getHeader().endToEndId());
    }

    @Test
    void decode_bufferSmallerThanHeaderSize_throwsException() {
        ByteBuf buffer = Unpooled.buffer(10);
        buffer.writeBytes(new byte[10]); // Only 10 bytes, less than 20

        DiameterProtocolException ex = assertThrows(DiameterProtocolException.class,
                () -> DiameterCodec.decode(buffer));
        assertTrue(ex.getMessage().contains("fewer than 20 bytes"));
    }

    @Test
    void decode_multipleAvps_parsedInOrder() {
        // Header (20) + AVP1 (12 bytes: code=263, len=12, 4 data) + AVP2 (12 bytes: code=268, len=12, 4 data)
        ByteBuf buffer = Unpooled.buffer(44);
        buffer.writeByte(0x01);
        buffer.writeMedium(44);
        buffer.writeByte(0x00);
        buffer.writeMedium(272);
        buffer.writeInt(4);
        buffer.writeInt(1);
        buffer.writeInt(2);

        // AVP 1: Session-Id (263), Mandatory, 4 bytes data
        buffer.writeInt(263);
        buffer.writeByte(0x40);
        buffer.writeMedium(12);
        buffer.writeInt(12345);

        // AVP 2: Result-Code (268), Mandatory, 4 bytes data
        buffer.writeInt(268);
        buffer.writeByte(0x40);
        buffer.writeMedium(12);
        buffer.writeInt(2001);

        DiameterMessage msg = DiameterCodec.decode(buffer);

        assertEquals(2, msg.getAvps().size());
        assertEquals(263, msg.getAvps().get(0).getCode());
        assertEquals(268, msg.getAvps().get(1).getCode());
        assertEquals(2001L, msg.getAvps().get(1).asUnsigned32());
    }
}
