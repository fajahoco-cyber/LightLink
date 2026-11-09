package app.lightlink.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * شبیه‌ساز کانال صوتی بلندگو ← هوا ← میکروفون:
 * بازتاب‌های اتاق (اکو)، نویز، تداخل تک‌فرکانس، افت دامنه، اختلاف کلاک دو گوشی (ppm)،
 * تغییر آهسته‌ی بهره (AGC)، سکوت اول و آخر و کوانتیزه‌کردن ۱۶ بیتی.
 */
class AudioChannelSimulator(
    private val gain: Double = 0.3,
    private val noiseRms: Double = 0.01,
    /** (تأخیر میلی‌ثانیه، بهره‌ی نسبی) هر بازتاب */
    private val echoes: List<Pair<Double, Double>> = listOf(12.0 to 0.35, 35.0 to 0.2, 80.0 to 0.12),
    private val clockPpm: Double = 80.0,
    private val interferenceHz: Double = 0.0,
    private val interferenceAmp: Double = 0.0,
    /** دامنه‌ی تغییر آهسته‌ی بهره (مثلاً ۰٫۳ یعنی بین ‎−۳۰٪‎ و ‎+۳۰٪‎). */
    private val agcDepth: Double = 0.0,
    private val leadSilenceMs: Int = 700,
    private val tailMs: Int = 700,
    private val sampleRate: Int = 44100,
    seed: Long = 1L,
) {
    private val random = Random(seed)

    fun process(pcm: ShortArray): FloatArray {
        val lead = leadSilenceMs * sampleRate / 1000
        val tail = tailMs * sampleRate / 1000
        val direct = FloatArray(lead + pcm.size + tail)
        for (i in pcm.indices) direct[lead + i] = pcm[i] / 32768f

        // بازتاب‌ها
        val withEcho = direct.copyOf()
        for ((delayMs, g) in echoes) {
            val d = (delayMs * sampleRate / 1000.0).toInt()
            for (i in d until direct.size) withEcho[i] += (direct[i - d] * g).toFloat()
        }

        // اختلاف کلاک: بازنمونه‌برداری خطی
        val ratio = 1.0 + clockPpm * 1e-6
        val outLen = (withEcho.size / ratio).toInt() - 2
        val out = FloatArray(outLen)
        val agcPhase = random.nextDouble() * 2 * PI
        for (j in 0 until outLen) {
            val pos = j * ratio
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            var v = withEcho[i0] * (1f - frac) + withEcho[i0 + 1] * frac
            v *= gain.toFloat()
            if (agcDepth > 0.0) v *= (1.0 + agcDepth * sin(2 * PI * 0.15 * j / sampleRate + agcPhase)).toFloat()
            if (interferenceAmp > 0.0) v += (interferenceAmp * sin(2 * PI * interferenceHz * j / sampleRate)).toFloat()
            v += (random.nextGaussian() * noiseRms).toFloat()
            // کوانتیزه‌کردن ۱۶ بیتی و بریدن
            val q = Math.round(v * 32768f).coerceIn(-32768, 32767)
            out[j] = q / 32768f
        }
        return out
    }

    /** فقط نویز (برای تست نبود سیگنال). */
    fun noiseOnly(seconds: Double): FloatArray {
        val len = (seconds * sampleRate).toInt()
        return FloatArray(len) { (random.nextGaussian() * noiseRms).toFloat() }
    }
}
