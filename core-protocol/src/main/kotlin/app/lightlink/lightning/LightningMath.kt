package app.lightlink.lightning

import kotlin.math.max

/** نتیجه‌ی یک اندازه‌گیری رعدوبرق. فاصله‌ها بر حسب متر. */
class LightningEstimate(
    val delaySeconds: Double,
    val temperatureC: Double,
    val distanceMeters: Double,
    /** بازه‌ی خطای زمان واکنش انسان (±۰٫۳ ثانیه روی تأخیر). */
    val minMeters: Double,
    val maxMeters: Double,
    val level: SafetyLevel,
    /** تأخیر خیلی زیاد یعنی احتمالاً نور و صدا مربوط به یک آذرخش نیستند؛ عدد قابل اتکا نیست. */
    val reliable: Boolean,
)

enum class SafetyLevel {
    /** ≤ ۱۰ ثانیه تقریباً ≤ ۳٫۴ کیلومتر: خطر فوری. */
    IMMEDIATE,

    /** ≤ ۳۰ ثانیه (≈ ۱۰ کیلومتر): هنوز در محدوده‌ی خطر؛ قاعده‌ی ۳۰-۳۰. */
    DANGER,

    /** بیش از ۳۰ ثانیه: دور، ولی طوفان ممکن است نزدیک شود. */
    DISTANT,
}

object LightningMath {
    const val REACTION_SECONDS = 0.3
    const val MAX_RELIABLE_DELAY_SECONDS = 90.0
    const val IMMEDIATE_SECONDS = 10.0
    const val DANGER_SECONDS = 30.0

    /** سرعت صوت در هوا (متر بر ثانیه) با تقریب خطی ‎331٫3 + 0٫606·T‎. */
    fun speedOfSound(temperatureC: Double): Double = 331.3 + 0.606 * temperatureC

    fun estimate(delaySeconds: Double, temperatureC: Double): LightningEstimate {
        require(delaySeconds >= 0.0) { "تأخیر منفی است" }
        require(temperatureC in -60.0..60.0) { "دما نامعتبر است" }
        val v = speedOfSound(temperatureC)
        val level = when {
            delaySeconds <= IMMEDIATE_SECONDS -> SafetyLevel.IMMEDIATE
            delaySeconds <= DANGER_SECONDS -> SafetyLevel.DANGER
            else -> SafetyLevel.DISTANT
        }
        return LightningEstimate(
            delaySeconds = delaySeconds,
            temperatureC = temperatureC,
            distanceMeters = v * delaySeconds,
            minMeters = v * max(0.0, delaySeconds - REACTION_SECONDS),
            maxMeters = v * (delaySeconds + REACTION_SECONDS),
            level = level,
            reliable = delaySeconds <= MAX_RELIABLE_DELAY_SECONDS,
        )
    }
}

/** یک ردیف تاریخچه. */
data class LightningRecord(val epochMillis: Long, val delayMillis: Long, val temperatureC: Int)

/** ذخیره‌ی متنی تاریخچه (هر خط: زمان|تأخیر|دما)؛ ردیف‌های خراب نادیده گرفته می‌شوند. */
object LightningHistoryCodec {
    const val MAX_RECORDS = 100

    fun encode(records: List<LightningRecord>): String =
        records.take(MAX_RECORDS).joinToString("\n") { "${it.epochMillis}|${it.delayMillis}|${it.temperatureC}" }

    fun decode(text: String): List<LightningRecord> =
        text.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val t = parts[0].toLongOrNull() ?: return@mapNotNull null
            val d = parts[1].toLongOrNull() ?: return@mapNotNull null
            val c = parts[2].toIntOrNull() ?: return@mapNotNull null
            if (d < 0 || c !in -60..60) null else LightningRecord(t, d, c)
        }.toList()

    /** جدیدترین اول؛ به سقف [MAX_RECORDS] محدود می‌شود. */
    fun add(records: List<LightningRecord>, record: LightningRecord): List<LightningRecord> =
        (listOf(record) + records).take(MAX_RECORDS)
}
