package app.lightlink.lightning

/** تبدیل تاریخ میلادی به شمسی (جلالی)؛ بدون وابستگی به کتابخانه. */
data class JalaliDate(val year: Int, val month: Int, val day: Int)

object Jalali {
    private val CUM = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
        require(gm in 1..12 && gd in 1..31) { "تاریخ میلادی نامعتبر است" }
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + CUM[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return JalaliDate(jy, jm, jd)
    }
}
