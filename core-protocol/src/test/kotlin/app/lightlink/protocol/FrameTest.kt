package app.lightlink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FrameTest {
    private val packet = Packet(msgId = 7, seq = 2, total = 5, payloadBytes = "abcdefgh".toByteArray(Charsets.US_ASCII))

    private fun decodeAll(bits: ByteArray): List<FrameEvent> {
        val decoder = FrameStreamDecoder()
        val events = mutableListOf<FrameEvent>()
        for (bit in bits) {
            val event = decoder.push(bit.toInt())
            if (event != null) events.add(event)
        }
        return events
    }

    @Test
    fun cleanFrameRoundTrips() {
        val events = decodeAll(FrameEncoder.encode(packet))
        assertEquals(1, events.size)
        val received = assertIs<FrameEvent.Received>(events[0])
        assertEquals(packet, received.packet)
        assertEquals(0, received.correctedBits)
    }

    @Test
    fun frameBitCountMatchesEncoder() {
        assertEquals(FrameEncoder.frameBitCount(packet.payload.size), FrameEncoder.encode(packet).size)
    }

    @Test
    fun leadingNoiseIsIgnored() {
        val noise = ByteArray(40) // صفر؛ با کلمه‌ی همگام‌سازی فاصله‌ی زیادی دارد
        val events = decodeAll(noise + FrameEncoder.encode(packet))
        assertEquals(1, events.filterIsInstance<FrameEvent.Received>().size)
    }

    @Test
    fun everySingleBitErrorInFrameIsSurvived() {
        val clean = FrameEncoder.encode(packet)
        for (i in clean.indices) {
            val noisy = clean.copyOf()
            noisy[i] = (noisy[i].toInt() xor 1).toByte()
            val received = decodeAll(noisy).filterIsInstance<FrameEvent.Received>()
            assertEquals(1, received.size, "bit $i")
            assertEquals(packet, received[0].packet, "bit $i")
        }
    }

    @Test
    fun doubleErrorInPayloadBlockIsRejectedByCrc() {
        val noisy = FrameEncoder.encode(packet)
        // اولین بلوک payload بعد از ۱۶ بیت sync و ۷۰ بیت سرآیند شروع می‌شود.
        val start = ProtocolConstants.SYNC_BITS +
            ProtocolConstants.HEADER_BYTES * ProtocolConstants.CODED_BITS_PER_BYTE
        noisy[start] = (noisy[start].toInt() xor 1).toByte()
        noisy[start + 1] = (noisy[start + 1].toInt() xor 1).toByte()
        val events = decodeAll(noisy)
        assertEquals(1, events.size)
        val rejected = assertIs<FrameEvent.Rejected>(events[0])
        assertEquals(RejectReason.BAD_CRC, rejected.reason)
    }

    @Test
    fun twoFramesBackToBackAreBothDecoded() {
        val second = Packet(7, 3, 5, "XYZ".toByteArray(Charsets.US_ASCII))
        val bits = FrameEncoder.encode(packet) + ByteArray(10) + FrameEncoder.encode(second)
        val received = decodeAll(bits).filterIsInstance<FrameEvent.Received>()
        assertEquals(listOf(packet, second), received.map { it.packet })
    }

    @Test
    fun emptyPayloadFrameWorks() {
        val empty = Packet(1, 0, 1, ByteArray(0))
        val received = decodeAll(FrameEncoder.encode(empty)).filterIsInstance<FrameEvent.Received>()
        assertEquals(1, received.size)
        assertTrue(received[0].packet.payload.isEmpty())
    }
}
