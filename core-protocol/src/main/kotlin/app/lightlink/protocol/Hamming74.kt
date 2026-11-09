package app.lightlink.protocol

/**
 * کد Hamming(7,4): هر نیمه‌بایت (۴ بیت) به ۷ بیت تبدیل می‌شود و در هر بلوک ۷ بیتی
 * یک خطای تک‌بیتی اصلاح می‌شود. چیدمان کلمه‌ی کد: p1 p2 d1 p3 d2 d3 d4.
 * خطای دوبیتی در یک بلوک ممکن است اشتباه اصلاح شود؛ CRC16 هر بسته چنین حالتی را کشف می‌کند.
 */
object Hamming74 {
    class Decoded(val data: ByteArray, val correctedBits: Int)

    /** هر بایت ورودی به ۱۴ بیت (دو بلوک) تبدیل می‌شود؛ ابتدا نیمه‌ی بالا. */
    fun encode(data: ByteArray): ByteArray {
        val out = ByteArray(data.size * ProtocolConstants.CODED_BITS_PER_BYTE)
        var pos = 0
        for (byte in data) {
            val v = byte.toInt() and 0xFF
            pos = encodeNibble(v shr 4, out, pos)
            pos = encodeNibble(v and 0x0F, out, pos)
        }
        return out
    }

    private fun encodeNibble(nibble: Int, out: ByteArray, start: Int): Int {
        val d1 = (nibble shr 3) and 1
        val d2 = (nibble shr 2) and 1
        val d3 = (nibble shr 1) and 1
        val d4 = nibble and 1
        val p1 = d1 xor d2 xor d4
        val p2 = d1 xor d3 xor d4
        val p3 = d2 xor d3 xor d4
        out[start] = p1.toByte()
        out[start + 1] = p2.toByte()
        out[start + 2] = d1.toByte()
        out[start + 3] = p3.toByte()
        out[start + 4] = d2.toByte()
        out[start + 5] = d3.toByte()
        out[start + 6] = d4.toByte()
        return start + 7
    }

    fun decode(bits: ByteArray): Decoded {
        require(bits.size % ProtocolConstants.CODED_BITS_PER_BYTE == 0) { "طول بیت‌ها باید مضرب ۱۴ باشد" }
        val out = ByteArray(bits.size / ProtocolConstants.CODED_BITS_PER_BYTE)
        var corrected = 0
        var pos = 0
        val c = IntArray(8) // اندیس ۱ تا ۷
        for (i in out.indices) {
            var value = 0
            repeat(2) {
                for (k in 1..7) c[k] = bits[pos + k - 1].toInt() and 1
                val s1 = c[1] xor c[3] xor c[5] xor c[7]
                val s2 = c[2] xor c[3] xor c[6] xor c[7]
                val s3 = c[4] xor c[5] xor c[6] xor c[7]
                val syndrome = s1 or (s2 shl 1) or (s3 shl 2)
                if (syndrome != 0) {
                    c[syndrome] = c[syndrome] xor 1
                    corrected++
                }
                val nibble = (c[3] shl 3) or (c[5] shl 2) or (c[6] shl 1) or c[7]
                value = (value shl 4) or nibble
                pos += 7
            }
            out[i] = value.toByte()
        }
        return Decoded(out, corrected)
    }
}
