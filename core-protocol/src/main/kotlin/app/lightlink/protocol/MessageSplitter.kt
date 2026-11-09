package app.lightlink.protocol

class MessageTooLongException(val sizeBytes: Int, val maxBytes: Int) :
    IllegalArgumentException("پیام $sizeBytes بایت است؛ سقف مجاز $maxBytes بایت است")

/** تقسیم متن (UTF-8) به بسته‌های کوچک. */
object MessageSplitter {
    fun split(
        text: String,
        msgId: Int,
        payloadSize: Int = ProtocolConstants.DEFAULT_PAYLOAD,
    ): List<Packet> {
        require(text.isNotEmpty()) { "پیام خالی است" }
        require(payloadSize in 1..ProtocolConstants.MAX_PAYLOAD) { "اندازه‌ی payload نامعتبر است" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > ProtocolConstants.MAX_MESSAGE_BYTES) {
            throw MessageTooLongException(bytes.size, ProtocolConstants.MAX_MESSAGE_BYTES)
        }
        val total = (bytes.size + payloadSize - 1) / payloadSize
        return List(total) { seq ->
            val from = seq * payloadSize
            val to = minOf(from + payloadSize, bytes.size)
            Packet(msgId, seq, total, bytes.copyOfRange(from, to))
        }
    }
}
