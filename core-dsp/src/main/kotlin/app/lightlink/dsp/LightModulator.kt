package app.lightlink.dsp

import app.lightlink.protocol.FrameEncoder
import app.lightlink.protocol.Packet

/**
 * تنظیمات کانال نور.
 *
 * هر بیت با کدگذاری Manchester در دو «نیم‌بیت» (symbol) فرستاده می‌شود:
 * بیت ۱ = روشن سپس خاموش، بیت ۰ = خاموش سپس روشن.
 * [symbolMs] مدت هر نیم‌بیت است، پس سرعت واقعی ۱۰۰۰ / (۲ × symbolMs) بیت بر ثانیه است
 * (با ۱۰۰ میلی‌ثانیه: ۵ بیت بر ثانیه).
 */
class LightConfig(
    val symbolMs: Int = 100,
    /** هر بسته چند بار فرستاده شود (حالت یک‌طرفه: گیرنده بسته‌های سالم را جمع می‌کند). */
    val repeats: Int = 2,
    /** تعداد بیت‌های متناوب ۰۱۰۱… قبل از هر فریم؛ باید زوج باشد. */
    val preambleBits: Int = 16,
    /** مدت خاموشی قبل از هر فریم بر حسب نیم‌بیت. */
    val gapSymbols: Int = 8,
) {
    init {
        require(symbolMs in 40..500) { "symbolMs باید بین ۴۰ تا ۵۰۰ باشد" }
        require(repeats in 1..5) { "repeats باید بین ۱ تا ۵ باشد" }
        require(preambleBits in 10..64 && preambleBits % 2 == 0) { "preambleBits باید زوج و بین ۱۰ تا ۶۴ باشد" }
        require(gapSymbols in 4..40) { "gapSymbols باید بین ۴ تا ۴۰ باشد" }
    }

    val bitsPerSecond: Double get() = 1000.0 / (2.0 * symbolMs)
}

/** برنامه‌ی کامل ارسال: سطح فلش در هر نیم‌بیت، و محل شروع هر فریم. */
class LightSchedule(
    val levels: BooleanArray,
    /** اندیس نیم‌بیتی که هر فریم از آنجا شروع می‌شود. */
    val frameStarts: IntArray,
    /** شماره‌ی بسته (seq) هر فریم. */
    val frameSeqs: IntArray,
    val symbolMs: Int,
) {
    val durationMs: Long get() = levels.size.toLong() * symbolMs

    /** اندیس فریمی که در لحظه‌ی [symbolIndex] در حال ارسال است. */
    fun frameAt(symbolIndex: Int): Int {
        var frame = 0
        for (i in frameStarts.indices) {
            if (frameStarts[i] <= symbolIndex) frame = i else break
        }
        return frame
    }
}

object LightModulator {
    /** بیت ۱ → [روشن، خاموش]؛ بیت ۰ → [خاموش، روشن] */
    fun manchester(bits: ByteArray): BooleanArray {
        val out = BooleanArray(bits.size * 2)
        for (i in bits.indices) {
            val one = bits[i].toInt() == 1
            out[2 * i] = one
            out[2 * i + 1] = !one
        }
        return out
    }

    /** فریم کامل یک بسته: خاموشی، preamble متناوب ۰۱۰۱…، سپس sync و بدنه. */
    fun frameLevels(packet: Packet, config: LightConfig): BooleanArray {
        val gap = BooleanArray(config.gapSymbols)
        val preambleBits = ByteArray(config.preambleBits) { i -> (i % 2).toByte() }
        return gap + manchester(preambleBits) + manchester(FrameEncoder.encode(packet))
    }

    fun schedule(packets: List<Packet>, config: LightConfig): LightSchedule {
        require(packets.isNotEmpty()) { "هیچ بسته‌ای برای ارسال نیست" }
        val frames = packets.map { frameLevels(it, config) }
        val starts = ArrayList<Int>()
        val seqs = ArrayList<Int>()
        var total = 0
        repeat(config.repeats) {
            for (i in packets.indices) {
                starts.add(total)
                seqs.add(packets[i].seq)
                total += frames[i].size
            }
        }
        val levels = BooleanArray(total + config.gapSymbols)
        var pos = 0
        repeat(config.repeats) {
            for (frame in frames) {
                frame.copyInto(levels, pos)
                pos += frame.size
            }
        }
        return LightSchedule(levels, starts.toIntArray(), seqs.toIntArray(), config.symbolMs)
    }
}
