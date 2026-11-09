package app.lightlink.dsp

import app.lightlink.protocol.AssembleResult
import app.lightlink.protocol.FrameEvent
import app.lightlink.protocol.FrameStreamDecoder
import app.lightlink.protocol.MessageAssembler

/**
 * زنجیره‌ی کامل گیرنده‌ی صدا بدون وابستگی به Android. چند دمدولاتور با سرعت‌های مختلف
 * ([AudioConfig.SUPPORTED_SYMBOL_MS]) هم‌زمان روی همان جریان صدا کار می‌کنند و هر کدام روی
 * preamble خودش قفل شد، همان پیش می‌رود؛ پس کاربر لازم نیست سرعت را روی گیرنده تنظیم کند.
 * رویدادها همان [ReceiverEvent] کانال نور است. ورود نمونه‌ها باید از یک رشته‌ی واحد باشد.
 */
class AudioReceiverCore(
    private val sampleRate: Int = 44100,
    private val f0: Double = 1200.0,
    private val f1: Double = 2200.0,
    symbolMsOptions: List<Int> = AudioConfig.SUPPORTED_SYMBOL_MS,
    private val minAmplitude: Double = 0.003,
    private val onEvent: (ReceiverEvent) -> Unit,
) {
    private inner class Pipeline(symbolMs: Int) {
        val decoder = FrameStreamDecoder()
        val demod: AudioDemodulator = AudioDemodulator(
            sampleRate = sampleRate,
            symbolMs = symbolMs.toDouble(),
            f0 = f0,
            f1 = f1,
            minAmplitude = minAmplitude,
        ) { bit -> onBit(this, bit) }

        init {
            demod.onLock = { onEvent(ReceiverEvent.Locked) }
            demod.onLost = {
                decoder.reset()
                onEvent(ReceiverEvent.Lost)
            }
        }
    }

    private val assembler = MessageAssembler()
    private val pipelines: List<Pipeline> = symbolMsOptions.map { Pipeline(it) }

    /** دامنه‌ی تقریبی بلندترین تون دیده‌شده (۰ تا ۱). */
    val toneAmplitude: Double get() = pipelines.maxOf { it.demod.toneAmplitude }

    val isLocked: Boolean get() = pipelines.any { it.demod.state == AudioDemodulator.State.LOCKED }

    fun pushSamples(pcm: ShortArray, length: Int) {
        for (i in 0 until length) {
            val x = pcm[i] / 32768f
            for (p in pipelines) p.demod.pushSample(x)
        }
    }

    fun pushSample(x: Float) {
        for (p in pipelines) p.demod.pushSample(x)
    }

    fun missingSeqs(): List<Int> = assembler.missingSeqs()

    fun resetMessage() {
        assembler.reset()
        resetAll()
    }

    private fun resetAll() {
        for (p in pipelines) {
            p.decoder.reset()
            p.demod.reset()
        }
    }

    private fun onBit(source: Pipeline, bit: Int) {
        val event = source.decoder.push(bit) ?: return
        // پس از هر فریم، همه‌ی خطوط دوباره دنبال preamble بعدی می‌گردند.
        resetAll()
        when (event) {
            is FrameEvent.Rejected -> onEvent(ReceiverEvent.PacketRejected(event.reason))
            is FrameEvent.Received -> handlePacket(event)
        }
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
