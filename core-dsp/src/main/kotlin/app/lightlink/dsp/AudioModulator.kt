package app.lightlink.dsp

import app.lightlink.protocol.FrameEncoder
import app.lightlink.protocol.Packet
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * تنظیمات کانال صدا (2-FSK با فاز پیوسته).
 * بیت ۰ = [f0]، بیت ۱ = [f1]. [symbolMs] مدت هر بیت است (۴۰ میلی‌ثانیه = ۲۵ بیت بر ثانیه).
 */
class AudioConfig(
    val symbolMs: Int = 40,
    val repeats: Int = 2,
    /** تعداد بیت‌های متناوب ۰۱۰۱… قبل از هر فریم؛ باید زوج باشد. */
    val preambleBits: Int = 24,
    val gapMs: Int = 300,
    val sampleRate: Int = 44100,
    val f0: Double = 1200.0,
    val f1: Double = 2200.0,
    val amplitude: Double = 0.85,
) {
    init {
        require(symbolMs in 5..100) { "symbolMs باید بین ۵ تا ۱۰۰ باشد" }
        require(repeats in 1..5) { "repeats باید بین ۱ تا ۵ باشد" }
        require(preambleBits in 12..64 && preambleBits % 2 == 0) { "preambleBits باید زوج و بین ۱۲ تا ۶۴ باشد" }
        require(gapMs in 100..2000) { "gapMs باید بین ۱۰۰ تا ۲۰۰۰ باشد" }
        require(sampleRate in 8000..48000) { "sampleRate نامعتبر است" }
        require(f0 > 200.0 && f1 > f0 && f1 < sampleRate / 2.0 - 500.0) { "فرکانس‌ها نامعتبرند" }
        require(amplitude in 0.05..0.99) { "amplitude باید بین ۰٫۰۵ تا ۰٫۹۹ باشد" }
    }

    val bitsPerSecond: Double get() = 1000.0 / symbolMs

    companion object {
        /** سرعت‌هایی که گیرنده هم‌زمان جست‌وجو می‌کند (میلی‌ثانیه بر بیت)، کند به تند. */
        val SUPPORTED_SYMBOL_MS: List<Int> = listOf(60, 40, 25, 15)
    }
}

/** برنامه‌ی کامل پخش: نمونه‌های PCM ۱۶ بیتی تک‌کاناله و محل شروع هر فریم. */
class AudioSchedule(
    val samples: ShortArray,
    val frameStarts: IntArray,
    val frameSeqs: IntArray,
    val sampleRate: Int,
) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate

    fun frameAt(sampleIndex: Int): Int {
        var frame = 0
        for (i in frameStarts.indices) {
            if (frameStarts[i] <= sampleIndex) frame = i else break
        }
        return frame
    }
}

object AudioModulator {
    private const val FADE_MS = 3.0

    /** بیت‌ها را به صدای FSK با فاز پیوسته (بدون کلیک) تبدیل می‌کند. */
    fun tones(bits: ByteArray, config: AudioConfig): FloatArray {
        val samplesPerBit = config.symbolMs * config.sampleRate / 1000.0
        val total = (bits.size * samplesPerBit).roundToInt()
        val out = FloatArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val bitIndex = minOf(bits.size - 1, (i / samplesPerBit).toInt())
            val f = if (bits[bitIndex].toInt() == 1) config.f1 else config.f0
            phase += 2.0 * PI * f / config.sampleRate
            out[i] = (sin(phase) * config.amplitude).toFloat()
        }
        val fade = (FADE_MS * config.sampleRate / 1000.0).roundToInt().coerceAtMost(total / 2)
        for (i in 0 until fade) {
            val g = i / fade.toFloat()
            out[i] *= g
            out[total - 1 - i] *= g
        }
        return out
    }

    /** فریم کامل یک بسته: preamble متناوب ۰۱۰۱… و سپس sync و بدنه (بدون سکوت). */
    fun frameTones(packet: Packet, config: AudioConfig): FloatArray {
        val preamble = ByteArray(config.preambleBits) { i -> (i % 2).toByte() }
        return tones(preamble + FrameEncoder.encode(packet), config)
    }

    fun schedule(packets: List<Packet>, config: AudioConfig): AudioSchedule {
        require(packets.isNotEmpty()) { "هیچ بسته‌ای برای ارسال نیست" }
        val frames = packets.map { frameTones(it, config) }
        val gap = (config.gapMs * config.sampleRate / 1000.0).roundToInt()

        var total = gap
        repeat(config.repeats) { for (f in frames) total += f.size + gap }

        val pcm = ShortArray(total)
        val starts = ArrayList<Int>()
        val seqs = ArrayList<Int>()
        var pos = gap
        repeat(config.repeats) {
            for (i in frames.indices) {
                starts.add(pos)
                seqs.add(packets[i].seq)
                val f = frames[i]
                for (k in f.indices) {
                    pcm[pos + k] = (f[k] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                pos += f.size + gap
            }
        }
        return AudioSchedule(pcm, starts.toIntArray(), seqs.toIntArray(), config.sampleRate)
    }
}
