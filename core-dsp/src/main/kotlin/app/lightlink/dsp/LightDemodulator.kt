package app.lightlink.dsp

import kotlin.math.abs

/**
 * دمدولاتور Manchester برای نمونه‌های روشنایی دوربین.
 *
 * ایده‌ها:
 * 1. روشنایی با ردیاب بیشینه/کمینه‌ی آهسته نرمال می‌شود؛ آستانه‌ی عبور وسط بازه است.
 * 2. لحظه‌ی هر لبه با درون‌یابی خطی بین دو نمونه (دقت زیرفریم) به دست می‌آید.
 * 3. preamble متناوب ۰۱۰۱… فقط لبه‌های میانِ بیت دارد و فاصله‌ی همه‌ی لبه‌ها برابر یک دوره‌ی بیت است.
 *    پس از دیدن [LOCK_EDGES] لبه‌ی هم‌فاصله، دوره و فاز با رگرسیون خطی تخمین زده می‌شود.
 * 4. هر بیت با مقایسه‌ی میانگین روشنایی در مرکز نیم‌بیت اول و دوم تعیین می‌شود
 *    (نیم‌بیت اول روشن‌تر → بیت ۱). این مقایسه‌ی نسبی به آستانه وابسته نیست.
 * 5. فاز با لبه‌ی میان هر بیت اصلاح می‌شود (PLL ساده)؛ اگر چند بیت پشت‌سرهم تقریباً بدون تغییر بود،
 *    قفل رها می‌شود و جست‌وجوی preamble از نو شروع می‌شود.
 *
 * زمان نمونه‌ها به نانوثانیه و یکنواخت است (مثلاً زمان‌سنج فریم دوربین).
 */
class LightDemodulator(
    private val minContrast: Double = 12.0,
    private val onBit: (Int) -> Unit,
) {
    enum class State { SEARCHING, LOCKED }

    var state: State = State.SEARCHING
        private set

    /** فراخوانی هنگام قفل شدن روی preamble. */
    var onLock: (() -> Unit)? = null

    /** فراخوانی هنگام از دست رفتن سیگنال (نه پس از پایان عادی یک فریم). */
    var onLost: (() -> Unit)? = null

    private class Sample(val t: Double, val v: Double)

    private val samples = ArrayDeque<Sample>()
    private val edges = ArrayDeque<Double>()

    private var baseNs = Long.MIN_VALUE
    private var lo = 0.0
    private var hi = 0.0
    private var levelsInit = false
    private var prev: Sample? = null
    private var hystHigh = false
    private var candidateT: Double? = null

    private var period = 0.0
    private var periodNominal = 0.0
    private var nextMid = 0.0
    private var weakCount = 0

    /** اختلاف بیشینه و کمینه‌ی روشنایی دیده‌شده؛ شاخص ساده‌ی کیفیت سیگنال. */
    val contrast: Double get() = if (levelsInit) hi - lo else 0.0

    /** دوره‌ی بیت تخمین‌زده‌شده بر حسب ثانیه (فقط در حالت قفل معنا دارد). */
    val bitPeriodSeconds: Double get() = if (state == State.LOCKED) period else 0.0

    /** بازگشت به جست‌وجوی preamble (ردیاب‌های روشنایی حفظ می‌شوند). */
    fun reset() {
        state = State.SEARCHING
        edges.clear()
        weakCount = 0
    }

    fun pushSample(timeNs: Long, brightness: Double) {
        if (baseNs == Long.MIN_VALUE) baseNs = timeNs
        val t = (timeNs - baseNs) / 1e9
        val sample = Sample(t, brightness)
        samples.addLast(sample)
        while (samples.size > 2 && t - samples.first().t > MAX_HISTORY_SECONDS) samples.removeFirst()

        updateLevels(brightness)
        detectEdges(sample)
        prev = sample

        if (state == State.LOCKED) decodeAvailableBits(t)
    }

    private fun updateLevels(v: Double) {
        if (!levelsInit) {
            lo = v
            hi = v
            levelsInit = true
            return
        }
        hi = if (v > hi) v else hi + (v - hi) * TRACK_RATE
        lo = if (v < lo) v else lo + (v - lo) * TRACK_RATE
    }

    private fun detectEdges(cur: Sample) {
        val contrastNow = hi - lo
        val th = (hi + lo) / 2.0
        val last = prev
        if (last != null && (last.v > th) != (cur.v > th)) {
            val dv = cur.v - last.v
            val crossing = if (dv != 0.0) last.t + (th - last.v) / dv * (cur.t - last.t) else cur.t
            candidateT = crossing.coerceIn(last.t, cur.t)
        }
        if (contrastNow < minContrast) {
            hystHigh = cur.v > th
            return
        }
        val band = HYSTERESIS * contrastNow
        if (!hystHigh && cur.v > th + band) {
            hystHigh = true
            onEdge(candidateT ?: cur.t)
            candidateT = null
        } else if (hystHigh && cur.v < th - band) {
            hystHigh = false
            onEdge(candidateT ?: cur.t)
            candidateT = null
        }
    }

    private fun onEdge(time: Double) {
        edges.addLast(time)
        if (state == State.SEARCHING) {
            while (edges.size > LOCK_EDGES) edges.removeFirst()
            if (edges.size == LOCK_EDGES) tryLock()
        } else {
            while (edges.size > 16) edges.removeFirst()
        }
    }

    private fun tryLock() {
        val e = edges.toList()
        val intervals = DoubleArray(e.size - 1) { e[it + 1] - e[it] }
        val mean = intervals.average()
        if (mean < MIN_PERIOD || mean > MAX_PERIOD) return
        if (intervals.any { abs(it - mean) > LOCK_TOLERANCE * mean }) return

        // رگرسیون خطی: e[i] ≈ intercept + period * i
        val n = e.size
        val meanI = (n - 1) / 2.0
        val meanE = e.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (i - meanI) * (e[i] - meanE)
            den += (i - meanI) * (i - meanI)
        }
        val p = num / den
        val lastFitted = meanE + p * ((n - 1) - meanI)

        period = p
        periodNominal = p
        nextMid = lastFitted + p
        weakCount = 0
        edges.clear()
        state = State.LOCKED
        onLock?.invoke()
    }

    private fun decodeAvailableBits(latest: Double) {
        while (state == State.LOCKED) {
            val half = period / 2.0
            val w = 0.25 * half
            if (latest < nextMid + half / 2.0 + w) return
            decodeOneBit(half, w)
        }
    }

    private fun decodeOneBit(half: Double, w: Double) {
        val a = windowMean(nextMid - half / 2.0, w)
        val b = windowMean(nextMid + half / 2.0, w)
        val range = (hi - lo).coerceAtLeast(1e-9)
        val confidence = abs(a - b) / range

        if (confidence < MIN_CONFIDENCE) weakCount++ else weakCount = 0
        if (weakCount >= MAX_WEAK_BITS) {
            reset()
            onLost?.invoke()
            return
        }

        // فاز را قبل از تحویل بیت به‌روز می‌کنیم؛ onBit ممکن است reset() صدا بزند.
        val mid = nextMid
        val found = edges.filter { abs(it - mid) <= 0.2 * period }.minByOrNull { abs(it - mid) }
        if (found != null) {
            val err = found - mid
            nextMid = mid + period + PHASE_GAIN * err
            period = (period + PERIOD_GAIN * err).coerceIn(periodNominal * 0.85, periodNominal * 1.15)
        } else {
            nextMid = mid + period
        }
        while (edges.isNotEmpty() && edges.first() < nextMid - 0.6 * period) edges.removeFirst()

        onBit(if (a > b) 1 else 0)
    }

    /** میانگین نمونه‌های درون [center - w, center + w]؛ اگر نمونه‌ای نبود، درون‌یابی خطی. */
    private fun windowMean(center: Double, w: Double): Double {
        var sum = 0.0
        var count = 0
        var before: Sample? = null
        var after: Sample? = null
        for (s in samples) {
            if (s.t >= center - w && s.t <= center + w) {
                sum += s.v
                count++
            }
            if (s.t <= center) before = s
            if (s.t > center && after == null) after = s
        }
        if (count > 0) return sum / count
        val b = before
        val a = after
        return when {
            b != null && a != null -> b.v + (a.v - b.v) * (center - b.t) / (a.t - b.t)
            b != null -> b.v
            a != null -> a.v
            else -> 0.0
        }
    }

    private companion object {
        const val MAX_HISTORY_SECONDS = 3.0
        const val TRACK_RATE = 0.01
        const val HYSTERESIS = 0.15
        const val LOCK_EDGES = 7
        const val LOCK_TOLERANCE = 0.2
        const val MIN_PERIOD = 0.08
        const val MAX_PERIOD = 1.1
        const val MIN_CONFIDENCE = 0.3
        const val MAX_WEAK_BITS = 6
        const val PHASE_GAIN = 0.4
        const val PERIOD_GAIN = 0.02
    }
}
