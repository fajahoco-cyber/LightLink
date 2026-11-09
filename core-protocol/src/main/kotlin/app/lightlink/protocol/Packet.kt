package app.lightlink.protocol

/**
 * یک بسته‌ی پروتکل. بدنه‌ی بسته به‌صورت بایت:
 * `[Version][MsgId][Seq][Total][Length][Payload...][CRC16 بالا][CRC16 پایین]`
 * و CRC روی همه‌ی بایت‌های قبل از خودش محاسبه می‌شود.
 */
class Packet(val msgId: Int, val seq: Int, val total: Int, payloadBytes: ByteArray) {
    val payload: ByteArray = payloadBytes.copyOf()

    init {
        require(msgId in 0..255) { "msgId باید بین ۰ تا ۲۵۵ باشد" }
        require(total in 1..255) { "total باید بین ۱ تا ۲۵۵ باشد" }
        require(seq in 0 until total) { "seq باید در بازه‌ی ۰ تا total-1 باشد" }
        require(payload.size <= ProtocolConstants.MAX_PAYLOAD) { "payload بیش از حد بزرگ است" }
    }

    fun bodyBytes(): ByteArray {
        val head = ByteArray(ProtocolConstants.HEADER_BYTES + payload.size)
        head[0] = ProtocolConstants.VERSION.toByte()
        head[1] = msgId.toByte()
        head[2] = seq.toByte()
        head[3] = total.toByte()
        head[4] = payload.size.toByte()
        payload.copyInto(head, ProtocolConstants.HEADER_BYTES)
        val crc = Crc16.compute(head)
        return head + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    override fun equals(other: Any?): Boolean =
        other is Packet &&
            msgId == other.msgId &&
            seq == other.seq &&
            total == other.total &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var h = msgId
        h = 31 * h + seq
        h = 31 * h + total
        h = 31 * h + payload.contentHashCode()
        return h
    }

    override fun toString(): String = "Packet(msgId=$msgId, seq=$seq, total=$total, payloadSize=${payload.size})"
}
