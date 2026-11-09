package app.lightlink.lightning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LightningTest {
    @Test
    fun speedOfSoundAt20Degrees() {
        assertEquals(343.42, LightningMath.speedOfSound(20.0), 1e-9)
    }

    @Test
    fun threeSecondsIsAboutOneKilometer() {
        val e = LightningMath.estimate(3.0, 20.0)
        assertEquals(1030.26, e.distanceMeters, 0.01)
        assertEquals(SafetyLevel.IMMEDIATE, e.level)
    }

    @Test
    fun reactionRangeIsClampedAtZero() {
        val e = LightningMath.estimate(0.1, 20.0)
        assertEquals(0.0, e.minMeters, 0.0)
        assertTrue(e.maxMeters > e.distanceMeters)
    }

    @Test
    fun safetyLevelsFollowThresholds() {
        assertEquals(SafetyLevel.IMMEDIATE, LightningMath.estimate(10.0, 20.0).level)
        assertEquals(SafetyLevel.DANGER, LightningMath.estimate(10.5, 20.0).level)
        assertEquals(SafetyLevel.DANGER, LightningMath.estimate(30.0, 20.0).level)
        assertEquals(SafetyLevel.DISTANT, LightningMath.estimate(30.5, 20.0).level)
    }

    @Test
    fun veryLongDelayIsMarkedUnreliable() {
        assertTrue(LightningMath.estimate(60.0, 20.0).reliable)
        assertFalse(LightningMath.estimate(120.0, 20.0).reliable)
    }

    @Test
    fun colderAirMeansSlowerSound() {
        assertTrue(LightningMath.estimate(5.0, 0.0).distanceMeters < LightningMath.estimate(5.0, 30.0).distanceMeters)
    }

    @Test
    fun historyCodecRoundTripAndIgnoresGarbage() {
        val records = listOf(LightningRecord(1000L, 4200L, 18), LightningRecord(500L, 9000L, -3))
        assertEquals(records, LightningHistoryCodec.decode(LightningHistoryCodec.encode(records)))
        assertEquals(1, LightningHistoryCodec.decode("bad\n1|2|3\n1|-5|3\n1|2|999").size)
        assertEquals(emptyList(), LightningHistoryCodec.decode(""))
    }

    @Test
    fun historyKeepsNewestAndCapsSize() {
        var list = emptyList<LightningRecord>()
        repeat(120) { list = LightningHistoryCodec.add(list, LightningRecord(it.toLong(), 1000L, 20)) }
        assertEquals(LightningHistoryCodec.MAX_RECORDS, list.size)
        assertEquals(119L, list.first().epochMillis)
    }

    @Test
    fun jalaliKnownDates() {
        assertEquals(JalaliDate(1405, 1, 1), Jalali.fromGregorian(2026, 3, 21))
        assertEquals(JalaliDate(1404, 1, 1), Jalali.fromGregorian(2025, 3, 21))
        assertEquals(JalaliDate(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate(1378, 10, 11), Jalali.fromGregorian(2000, 1, 1))
        assertEquals(JalaliDate(1405, 8, 19), Jalali.fromGregorian(2026, 11, 10))
        assertEquals(JalaliDate(1404, 12, 29), Jalali.fromGregorian(2026, 3, 20))
    }
}
