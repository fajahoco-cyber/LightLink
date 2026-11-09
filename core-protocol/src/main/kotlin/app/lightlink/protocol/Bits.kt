package app.lightlink.protocol

/** ابزار تبدیل بین بایت و بیت. هر بیت یک بایت با مقدار ۰ یا ۱ است و بیت پرارزش‌تر اول می‌آید. */
object Bits {
    fun fromBytes(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size * 8)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            for (b in 0 until 8) {
                out[i * 8 + b] = ((v shr (7 - b)) and 1).toByte()
            }
        }
        return out
    }

    fun toBytes(bits: ByteArray): ByteArray {
        require(bits.size % 8 == 0) { "تعداد بیت‌ها باید مضرب ۸ باشد" }
        val out = ByteArray(bits.size / 8)
        for (i in out.indices) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or (bits[i * 8 + b].toInt() and 1)
            }
            out[i] = v.toByte()
        }
        return out
    }

    /** [count] بیت پایین‌ترین بیت‌های [value]، با بیت پرارزش‌تر اول. */
    fun fromInt(value: Int, count: Int): ByteArray {
        require(count in 1..31)
        return ByteArray(count) { i -> ((value shr (count - 1 - i)) and 1).toByte() }
    }
}
