package app.lightlink.protocol

enum class RejectReason { BAD_VERSION, BAD_LENGTH, BAD_CRC, BAD_SEQUENCE }

sealed interface FrameEvent {
    /** بسته‌ی سالم؛ [correctedBits] تعداد بیت‌های اصلاح‌شده توسط Hamming است. */
    class Received(val packet: Packet, val correctedBits: Int) : FrameEvent

    class Rejected(val reason: RejectReason) : FrameEvent
}

/**
 * رمزگشای جریانی: بیت‌ها را یکی‌یکی می‌گیرد، کلمه‌ی همگام‌سازی را پیدا می‌کند،
 * سرآیند و بدنه را با Hamming رمزگشایی می‌کند، CRC را می‌سنجد و رویداد برمی‌گرداند.
 *
 * [syncTolerance] حداکثر تعداد بیت اشتباه مجاز در کلمه‌ی همگام‌سازی است.
 * پس از هر فریم (سالم یا مردود) رمزگشا دوباره دنبال کلمه‌ی همگام‌سازی می‌گردد.
 */
class FrameStreamDecoder(private val syncTolerance: Int = 1) {
    private enum class State { SEARCH, HEADER, BODY }

    private var state = State.SEARCH
    private var window = 0
    private var windowFill = 0
    private var buffer = ByteArray(0)
    private var filled = 0
    private var header = ByteArray(0)
    private var headerCorrected = 0

    init {
        require(syncTolerance in 0..3) { "syncTolerance باید بین ۰ تا ۳ باشد" }
    }

    fun reset() {
        state = State.SEARCH
        window = 0
        windowFill = 0
        filled = 0
    }

    /** یک بیت (۰ یا ۱) می‌گیرد و در صورت تکمیل شدن یک فریم رویداد برمی‌گرداند. */
    fun push(bit: Int): FrameEvent? {
        require(bit == 0 || bit == 1) { "بیت باید ۰ یا ۱ باشد" }
        when (state) {
            State.SEARCH -> {
                window = ((window shl 1) or bit) and 0xFFFF
                if (windowFill < ProtocolConstants.SYNC_BITS) windowFill++
                if (windowFill == ProtocolConstants.SYNC_BITS &&
                    Integer.bitCount(window xor ProtocolConstants.SYNC_WORD) <= syncTolerance
                ) {
                    state = State.HEADER
                    buffer = ByteArray(ProtocolConstants.HEADER_BYTES * ProtocolConstants.CODED_BITS_PER_BYTE)
                    filled = 0
                }
                return null
            }

            State.HEADER -> {
                buffer[filled++] = bit.toByte()
                if (filled < buffer.size) return null
                val decoded = Hamming74.decode(buffer)
                headerCorrected = decoded.correctedBits
                header = decoded.data
                val version = header[0].toInt() and 0xFF
                val length = header[4].toInt() and 0xFF
                if (version != ProtocolConstants.VERSION) return fail(RejectReason.BAD_VERSION)
                if (length > ProtocolConstants.MAX_PAYLOAD) return fail(RejectReason.BAD_LENGTH)
                buffer = ByteArray((length + ProtocolConstants.CRC_BYTES) * ProtocolConstants.CODED_BITS_PER_BYTE)
                filled = 0
                state = State.BODY
                return null
            }

            State.BODY -> {
                buffer[filled++] = bit.toByte()
                if (filled < buffer.size) return null
                return finishBody()
            }
        }
        return null
    }

    private fun finishBody(): FrameEvent {
        val decoded = Hamming74.decode(buffer)
        val length = header[4].toInt() and 0xFF
        val payload = decoded.data.copyOfRange(0, length)
        val crcReceived =
            ((decoded.data[length].toInt() and 0xFF) shl 8) or (decoded.data[length + 1].toInt() and 0xFF)
        val covered = header + payload
        if (Crc16.compute(covered) != crcReceived) return fail(RejectReason.BAD_CRC)

        val msgId = header[1].toInt() and 0xFF
        val seq = header[2].toInt() and 0xFF
        val total = header[3].toInt() and 0xFF
        if (total < 1 || seq >= total) return fail(RejectReason.BAD_SEQUENCE)

        val event = FrameEvent.Received(Packet(msgId, seq, total, payload), headerCorrected + decoded.correctedBits)
        reset()
        return event
    }

    private fun fail(reason: RejectReason): FrameEvent {
        reset()
        return FrameEvent.Rejected(reason)
    }
}
