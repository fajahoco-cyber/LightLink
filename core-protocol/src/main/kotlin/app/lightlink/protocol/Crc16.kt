package app.lightlink.protocol

/**
 * CRC-16/CCITT-FALSE: چندجمله‌ای 0x1021، مقدار اولیه 0xFFFF، بدون بازتاب بیت‌ها و بدون XOR نهایی.
 * برای صحت‌سنجی هر بسته در پروتکل نور و صدا استفاده می‌شود.
 */
object Crc16 {
    private const val POLY = 0x1021
    private const val INIT = 0xFFFF

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) { "محدوده‌ی نامعتبر" }
        var crc = INIT
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor POLY else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
