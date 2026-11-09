package app.lightlink.protocol

/**
 * تبدیل یک بسته به جریان بیت برای ارسال (بدون Preamble؛ آن را لایه‌ی فیزیکی نور/صدا اضافه می‌کند):
 * کلمه‌ی همگام‌سازی خام ۱۶ بیتی + بدنه‌ی بسته با Hamming(7,4).
 */
object FrameEncoder {
    fun encode(packet: Packet): ByteArray {
        val sync = Bits.fromInt(ProtocolConstants.SYNC_WORD, ProtocolConstants.SYNC_BITS)
        val body = Hamming74.encode(packet.bodyBytes())
        return sync + body
    }

    fun frameBitCount(payloadSize: Int): Int =
        ProtocolConstants.SYNC_BITS +
            (ProtocolConstants.HEADER_BYTES + payloadSize + ProtocolConstants.CRC_BYTES) *
            ProtocolConstants.CODED_BITS_PER_BYTE
}
