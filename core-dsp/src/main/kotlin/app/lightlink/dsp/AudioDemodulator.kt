package app.lightlink.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * دمدولاتور 2-FSK با الگوریتم Goertzel برای یک سرعت مشخص ([symbolMs] نامی).
 *
 * 1. هر ⅛ طول بیت، انرژی دو فرکانس در پنجره‌ی آخر به‌اندازه‌ی یک بیت با Goertzel محاسبه می‌شود:
 *    s = (P1 − P0) / (P1 + P0) بین ‎−۱‎ و ‎+۱‎ (نسبی؛ به بلندی صدا و AGC میکروفون وابسته نیست).
 * 2. وجود سیگنال با مقایسه‌ی دامنه‌ی تون با کف نویز آهسته‌ی تخمینی و حداقل مطلق تعیین می‌شود.
 * 3. preamble متناوب ۰۱۰۱… عبور s از صفر را دقیقاً روی مرز هر بیت می‌گذارد. هفت عبور هم‌فاصله
 *    (با دوره‌ی نزدیک به [symbolMs]) قفل را برقرار می‌کند؛ دوره و فاز با رگرسیون خطی تخمین زده می‌شود،
 *    پس اختلاف کلاک دو گوشی جبران می‌شود.
 * 4. ساعت بیت با رگرسیون خطی روی ۴۸ عبور اخیر از صفر (زمان مرز بیت‌ها) تخمین زده می‌شود؛ پس خطای تکی
 *    یک عبور نویزی دوره را خراب نمی‌کند و دوره‌های طولانی بدون تغییر بیت با ساعت دقیق پر می‌شوند.
 * 5. هر بیت از میانگین s در مرکز بیت تعیین می‌شود.
 *
 * نمونه‌ها float بین ‎−۱‎ تا ‎+۱‎ هستند.
 */
class AudioDemodulator(
    private val sampleRate: Int,
    private val symbolMs: Double,
    private val f0: Double,
    private val f1: Double,
    private val minAmplitude: Double = 0.003,
    private val onBit: (Int) -> Unit,
) {
    enum class State { SEARCHING, LOCKED }

    var state: State = State.SEARCHING
        private set

    var onLock: (() -> Unit)? = null
    var onLost: (() -> Unit)? = null

    private val n: Int = (sampleRate * symbolMs / 1000.0).roundToInt()
    private val hop: Int = maxOf(1, n / 8)
    private val nominalPeriod: Double = n.toDouble() / sampleRate

    private val ring = FloatArray(n)
    private var ringPos = 0
    private var pushed = 0L

    private val coef0 = 2.0 * cos(2.0 * PI * f0 / sampleRate)
    private val coef1 = 2.0 * cos(2.0 * PI * f1 / sampleRate)

    private class Hop(val t: Double, val s: Double, val present: Boolean)

    private val hops = ArrayDeque<Hop>()
    private val crossings = ArrayDeque<Double>()

    private var noiseFloor = -1.0
    private var prevHop: Hop? = null

    /** مرز بیت‌ها: زمان مرز k‌ام ≈ fitA + fitB * k (k=۰ همان آخرین عبور preamble هنگام قفل). */
    private var fitA = 0.0
    private var fitB = 0.0
    private var periodLocked = 0.0
    private var symbolIndex = 0
    private val boundaryPairs = ArrayDeque<DoubleArray>()
    private var weakCount = 0

    /** دامنه‌ی تقریبی بلندترین تون در آخرین پنجره (۰ تا ۱)؛ برای نمایش سطح ورودی. */
    var toneAmplitude: Double = 0.0
        private set

    fun reset() {
        state = State.SEARCHING
        crossings.clear()
        weakCount = 0
    }

    fun pushSample(x: Float) {
        ring[ringPos] = x
        ringPos = (ringPos + 1) % n
        pushed++
        if (pushed >= n && pushed % hop == 0L) onHop()
    }

    private fun onHop() {
        var s10 = 0.0
        var s20 = 0.0
        var s11 = 0.0
        var s21 = 0.0
        var idx = ringPos // قدیمی‌ترین نمونه
        for (i in 0 until n) {
            val x = ring[idx].toDouble()
            val a = x + coef0 * s10 - s20
            s20 = s10
            s10 = a
            val b = x + coef1 * s11 - s21
            s21 = s11
            s11 = b
            idx++
            if (idx == n) idx = 0
        }
        val p0 = s10 * s10 + s20 * s20 - coef0 * s10 * s20
        val p1 = s11 * s11 + s21 * s21 - coef1 * s11 * s21
        val total = p0 + p1
        val s = (p1 - p0) / (total + 1e-18)

        val amp = 2.0 * sqrt(maxOf(total, 0.0)) / n
        toneAmplitude = amp
        // کف نویز: به پایین سریع و به بالا بسیار کند؛ وگرنه وسط یک تون طولانی به سطح خود سیگنال می‌رسد.
        noiseFloor = when {
            noiseFloor < 0.0 -> amp
            amp < noiseFloor -> noiseFloor + (amp - noiseFloor) * NOISE_RATE_DOWN
            else -> noiseFloor + (amp - noiseFloor) * NOISE_RATE_UP
        }
        val present = amp >= minAmplitude && amp >= PRESENCE_RATIO * noiseFloor

        val t = (pushed - n / 2.0) / sampleRate
        val cur = Hop(t, s, present)
        hops.addLast(cur)
        while (hops.size > 2 && t - hops.first().t > MAX_HISTORY_SECONDS) hops.removeFirst()

        detectCrossing(cur)
        prevHop = cur
        if (state == State.LOCKED) decodeAvailableBits(t)
    }

    private fun detectCrossing(cur: Hop) {
        val p = prevHop ?: return
        if (!p.present || !cur.present) return
        if ((p.s > 0.0) == (cur.s > 0.0)) return
        // عبور از صفر فقط وقتی معتبر است که تغییر از قطب به قطب مخالف باشد، نه نوسان کوچک اطراف صفر
        if (abs(p.s) < 0.15 && abs(cur.s) < 0.15) return
        val ds = cur.s - p.s
        val time = if (ds != 0.0) p.t + (0.0 - p.s) / ds * (cur.t - p.t) else cur.t
        crossings.addLast(time.coerceIn(p.t, cur.t))
        if (state == State.SEARCHING) {
            while (crossings.size > LOCK_CROSSINGS) crossings.removeFirst()
            if (crossings.size == LOCK_CROSSINGS) tryLock()
        } else {
            while (crossings.size > 16) crossings.removeFirst()
        }
    }

    private fun tryLock() {
        val e = crossings.toList()
        val intervals = DoubleArray(e.size - 1) { e[it + 1] - e[it] }
        val mean = intervals.average()
        if (abs(mean - nominalPeriod) > NOMINAL_TOLERANCE * nominalPeriod) return
        if (intervals.any { abs(it - mean) > LOCK_TOLERANCE * mean }) return

        // آخرین عبور، مرز شماره‌ی صفر است؛ عبورهای قبلی اندیس منفی می‌گیرند.
        boundaryPairs.clear()
        for (i in e.indices) boundaryPairs.addLast(doubleArrayOf((i - (e.size - 1)).toDouble(), e[i]))
        periodLocked = mean
        fitA = e.last()
        fitB = mean
        refit()
        symbolIndex = 0
        weakCount = 0
        crossings.clear()
        state = State.LOCKED
        onLock?.invoke()
    }

    /** رگرسیون خطی روی مرزهای اخیر؛ اگر دوره‌ی تخمینی غیرمنطقی بود، تخمین قبلی می‌ماند. */
    private fun refit() {
        val m = boundaryPairs.size
        if (m < 4) return
        var sumK = 0.0
        var sumT = 0.0
        for (pair in boundaryPairs) {
            sumK += pair[0]
            sumT += pair[1]
        }
        val meanK = sumK / m
        val meanT = sumT / m
        var num = 0.0
        var den = 0.0
        for (pair in boundaryPairs) {
            num += (pair[0] - meanK) * (pair[1] - meanT)
            den += (pair[0] - meanK) * (pair[0] - meanK)
        }
        if (den < 1e-9) return
        val b = num / den
        if (abs(b - periodLocked) > MAX_PERIOD_DEVIATION * periodLocked) return
        fitB = b
        fitA = meanT - b * meanK
    }

    private fun centerOf(index: Int): Double = fitA + (index + 0.5) * fitB

    private fun decodeAvailableBits(latest: Double) {
        while (state == State.LOCKED) {
            if (latest < centerOf(symbolIndex) + fitB / 8.0 + hop.toDouble() / sampleRate) return
            decodeOneBit()
        }
    }

    private fun decodeOneBit() {
        val index = symbolIndex
        val center = centerOf(index)
        val halfWindow = fitB / 8.0 + 1e-9
        var sum = 0.0
        var count = 0
        var allPresent = true
        for (h in hops) {
            if (abs(h.t - center) <= halfWindow) {
                sum += h.s
                count++
                if (!h.present) allPresent = false
            }
        }
        val value = if (count > 0) sum / count else 0.0

        if (count == 0 || !allPresent || abs(value) < MIN_CONFIDENCE) weakCount++ else weakCount = 0
        if (weakCount >= MAX_WEAK_BITS) {
            reset()
            onLost?.invoke()
            return
        }

        // مرز ابتدای این بیت؛ اگر عبوری نزدیکش دیده شد، به مرجع رگرسیون اضافه می‌شود.
        if (index > 0) {
            val boundary = fitA + index * fitB
            val found = crossings
                .filter { abs(it - boundary) <= BOUNDARY_MATCH * fitB }
                .minByOrNull { abs(it - boundary) }
            if (found != null) {
                boundaryPairs.addLast(doubleArrayOf(index.toDouble(), found))
                while (boundaryPairs.size > FIT_PAIRS) boundaryPairs.removeFirst()
                refit()
            }
        }
        symbolIndex = index + 1
        while (crossings.isNotEmpty() && crossings.first() < fitA + (symbolIndex - 1) * fitB - 0.3 * fitB) {
            crossings.removeFirst()
        }

        onBit(if (value > 0.0) 1 else 0)
    }

    private companion object {
        const val MAX_HISTORY_SECONDS = 1.0
        const val NOISE_RATE_DOWN = 0.05
        const val NOISE_RATE_UP = 0.00005
        const val PRESENCE_RATIO = 4.0
        const val LOCK_CROSSINGS = 7
        const val LOCK_TOLERANCE = 0.15
        const val NOMINAL_TOLERANCE = 0.25
        const val MIN_CONFIDENCE = 0.3
        const val MAX_WEAK_BITS = 6
        const val BOUNDARY_MATCH = 0.25
        const val FIT_PAIRS = 48
        const val MAX_PERIOD_DEVIATION = 0.03
    }
}
