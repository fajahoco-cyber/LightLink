package app.lightlink.dsp

import app.lightlink.protocol.AssembleResult
import app.lightlink.protocol.FrameEvent
import app.lightlink.protocol.FrameStreamDecoder
import app.lightlink.protocol.MessageAssembler

/**
 * بخش مشترک گیرنده‌ها: جریان بیت ← رمزگشای فریم ← جمع‌کننده‌ی پیام ← رویداد.
 * کانال نور و صدا هر کدام فقط بیت تولید می‌کنند و آن را به اینجا می‌دهند.
 */
class BitStreamReceiver(private val onEvent: (ReceiverEvent) -> Unit) {
    private val frameDecoder = FrameStreamDecoder()
    private val assembler = MessageAssembler()

    /** بیت بعدی را می‌گیرد؛ اگر با این بیت یک فریم (سالم یا مردود) تمام شد true برمی‌گرداند. */
    fun pushBit(bit: Int): Boolean {
        val event = frameDecoder.push(bit) ?: return false
        when (event) {
            is FrameEvent.Rejected -> onEvent(ReceiverEvent.PacketRejected(event.reason))
            is FrameEvent.Received -> handlePacket(event)
        }
        return true
    }

    fun signalLocked() {
        onEvent(ReceiverEvent.Locked)
    }

    fun signalLost() {
        frameDecoder.reset()
        onEvent(ReceiverEvent.Lost)
    }

    /** شماره‌ی بسته‌هایی که برای پیام در حال دریافت هنوز نرسیده‌اند. */
    fun missingSeqs(): List<Int> = assembler.missingSeqs()

    /** پیام نیمه‌کاره و حالت رمزگشا را پاک می‌کند. */
    fun reset() {
        assembler.reset()
        frameDecoder.reset()
    }

    private fun handlePacket(event: FrameEvent.Received) {
        val packet = event.packet
        when (val result = assembler.accept(packet)) {
            is AssembleResult.Progress ->
                onEvent(ReceiverEvent.PacketAccepted(packet.seq, packet.total, result.received, event.correctedBits))

            AssembleResult.Duplicate, AssembleResult.Conflict -> onEvent(ReceiverEvent.PacketDuplicate)

            is AssembleResult.Complete -> {
                onEvent(ReceiverEvent.PacketAccepted(packet.seq, packet.total, packet.total, event.correctedBits))
                onEvent(ReceiverEvent.MessageComplete(result.text))
            }

            AssembleResult.InvalidText -> onEvent(ReceiverEvent.MessageInvalid)
        }
    }
}
