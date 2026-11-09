package app.lightlink.dsp

import app.lightlink.protocol.RejectReason

sealed interface ReceiverEvent {
    /** روی preamble قفل شد. */
    data object Locked : ReceiverEvent

    /** سیگنال از دست رفت. */
    data object Lost : ReceiverEvent

    /** بسته‌ی سالم پذیرفته شد. */
    class PacketAccepted(
        val seq: Int,
        val total: Int,
        val receivedCount: Int,
        val correctedBits: Int,
    ) : ReceiverEvent

    /** بسته‌ی سالم ولی تکراری (یا ناسازگار با پیام در حال دریافت). */
    data object PacketDuplicate : ReceiverEvent

    /** فریم رد شد (CRC و …). */
    class PacketRejected(val reason: RejectReason) : ReceiverEvent

    /** همه‌ی بسته‌ها رسید و متن معتبر است. */
    class MessageComplete(val text: String) : ReceiverEvent

    /** همه‌ی بسته‌ها رسید ولی متن UTF-8 معتبر نبود. */
    data object MessageInvalid : ReceiverEvent
}

/**
 * زنجیره‌ی کامل گیرنده‌ی نور بدون وابستگی به Android:
 * نمونه‌ی روشنایی ← دمدولاتور ← بخش مشترک گیرنده (رمزگشای فریم و جمع‌کننده‌ی پیام).
 * ورود نمونه‌ها باید از یک رشته‌ی (thread) واحد باشد.
 */
class LightReceiverCore(
    minContrast: Double = 12.0,
    onEvent: (ReceiverEvent) -> Unit,
) {
    private val sink = BitStreamReceiver(onEvent)
    private val demodulator: LightDemodulator = LightDemodulator(minContrast) { bit -> onBit(bit) }

    init {
        demodulator.onLock = { sink.signalLocked() }
        demodulator.onLost = { sink.signalLost() }
    }

    val isLocked: Boolean get() = demodulator.state == LightDemodulator.State.LOCKED
    val contrast: Double get() = demodulator.contrast

    fun pushSample(timeNs: Long, brightness: Double) {
        demodulator.pushSample(timeNs, brightness)
    }

    fun missingSeqs(): List<Int> = sink.missingSeqs()

    /** پیام نیمه‌کاره و حالت رمزگشا را پاک می‌کند. */
    fun resetMessage() {
        sink.reset()
        demodulator.reset()
    }

    private fun onBit(bit: Int) {
        // پس از هر فریم (سالم یا مردود) دوباره دنبال preamble بعدی می‌گردیم.
        if (sink.pushBit(bit)) demodulator.reset()
    }
}
