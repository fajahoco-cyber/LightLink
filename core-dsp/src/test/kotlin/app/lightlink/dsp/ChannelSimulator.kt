package app.lightlink.dsp

import java.util.Random

/** نمونه‌ی روشنایی شبیه‌سازی‌شده‌ی دوربین. */
class SimSample(val timeNs: Long, val value: Double)

/**
 * شبیه‌ساز کانال نور: فلش (با جیتر زمان‌بندی فرستنده) ← نور محیط و نویز ← دوربین با نرخ فریم
 * مشخص، جیتر زمان نمونه و انتگرال‌گیری در مدت نوردهی.
 */
class ChannelSimulator(
    private val fps: Double = 30.0,
    private val exposureMs: Double = 8.0,
    private val low: Double = 25.0,
    private val high: Double = 190.0,
    private val noise: Double = 4.0,
    private val senderJitterMs: Double = 6.0,
    private val frameJitterMs: Double = 3.0,
    /** افزایش خطی روشنایی محیط در کل مدت ارسال (برای شبیه‌سازی تغییر نور اتاق). */
    private val drift: Double = 0.0,
    private val leadSeconds: Double = 1.3,
    seed: Long = 1L,
) {
    private val random = Random(seed)

    fun simulate(levels: BooleanArray, symbolMs: Int): List<SimSample> {
        val symbolSec = symbolMs / 1000.0
        // مرز هر نیم‌بیت با جیتر فرستنده (تأخیر متغیر setTorchMode)
        val boundaries = DoubleArray(levels.size + 1) { i ->
            leadSeconds + i * symbolSec + (if (i == 0) 0.0 else random.nextGaussian() * senderJitterMs / 1000.0)
        }
        for (i in 1 until boundaries.size) if (boundaries[i] <= boundaries[i - 1]) boundaries[i] = boundaries[i - 1] + 1e-4

        fun levelAt(t: Double): Boolean {
            if (t < boundaries[0] || t >= boundaries[boundaries.size - 1]) return false
            var lo = 0
            var hi = boundaries.size - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (boundaries[mid] <= t) lo = mid else hi = mid
            }
            return levels[lo]
        }

        val total = boundaries[boundaries.size - 1] + 1.5
        val out = ArrayList<SimSample>()
        var frame = 0
        while (true) {
            val t = frame / fps + random.nextGaussian() * frameJitterMs / 1000.0 + 0.0123
            if (t > total) break
            frame++
            // انتگرال‌گیری در مدت نوردهی
            val steps = 8
            var acc = 0.0
            for (k in 0 until steps) {
                val ts = t + exposureMs / 1000.0 * (k + 0.5) / steps
                acc += if (levelAt(ts)) high else low
            }
            val ambient = drift * (t / total)
            val v = acc / steps + ambient + random.nextGaussian() * noise
            out.add(SimSample((t * 1e9).toLong() + 5_000_000_000L, v))
        }
        return out.sortedBy { it.timeNs }
    }

    /** فقط نویز بدون سیگنال. */
    fun noiseOnly(seconds: Double, level: Double = 60.0): List<SimSample> {
        val out = ArrayList<SimSample>()
        var frame = 0
        while (frame / fps < seconds) {
            val t = frame / fps
            frame++
            out.add(SimSample((t * 1e9).toLong() + 5_000_000_000L, level + random.nextGaussian() * noise))
        }
        return out
    }
}
