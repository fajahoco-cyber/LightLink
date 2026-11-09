package app.lightlink.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HammingBitsTest {
    @Test
    fun bitsRoundTrip() {
        val data = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte(), 0xA5.toByte())
        assertContentEquals(data, Bits.toBytes(Bits.fromBytes(data)))
    }

    @Test
    fun fromIntIsMsbFirst() {
        assertContentEquals(byteArrayOf(1, 0, 1, 1), Bits.fromInt(0b1011, 4))
    }

    @Test
    fun everyByteRoundTripsWithoutErrors() {
        val all = ByteArray(256) { it.toByte() }
        val decoded = Hamming74.decode(Hamming74.encode(all))
        assertContentEquals(all, decoded.data)
        assertEquals(0, decoded.correctedBits)
    }

    @Test
    fun everySingleBitErrorIsCorrected() {
        val data = byteArrayOf(0x00, 0x5A, 0xC3.toByte(), 0xFF.toByte())
        val clean = Hamming74.encode(data)
        for (i in clean.indices) {
            val noisy = clean.copyOf()
            noisy[i] = (noisy[i].toInt() xor 1).toByte()
            val decoded = Hamming74.decode(noisy)
            assertContentEquals(data, decoded.data, "bit $i")
            assertEquals(1, decoded.correctedBits, "bit $i")
        }
    }
}
