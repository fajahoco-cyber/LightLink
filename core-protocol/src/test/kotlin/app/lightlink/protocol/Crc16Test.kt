package app.lightlink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Crc16Test {
    @Test
    fun standardCheckValue() {
        // مقدار مرجع استاندارد CRC-16/CCITT-FALSE برای رشته‌ی "123456789"
        assertEquals(0x29B1, Crc16.compute("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun emptyInputReturnsInitialValue() {
        assertEquals(0xFFFF, Crc16.compute(ByteArray(0)))
    }

    @Test
    fun singleBitFlipChangesCrc() {
        val original = "سلام".toByteArray(Charsets.UTF_8)
        val corrupted = original.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertNotEquals(Crc16.compute(original), Crc16.compute(corrupted))
    }

    @Test
    fun offsetAndLengthSelectSubrange() {
        val data = "xx123456789yy".toByteArray(Charsets.US_ASCII)
        assertEquals(0x29B1, Crc16.compute(data, offset = 2, length = 9))
    }
}
