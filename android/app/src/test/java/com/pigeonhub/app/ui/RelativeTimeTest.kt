package com.pigeonhub.app.ui

import com.pigeonhub.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MVP-011.5A: relative-time bucket boundaries with a fake clock. */
class RelativeTimeTest {

    private val t0 = 1_700_000_000_000L

    private fun at(offsetMs: Long): RelativeTime = RelativeTime.compute(t0, t0 + offsetMs)

    @Test
    fun `under one minute is just now`() {
        assertEquals(RelativeTime.JustNow, at(0L))
        assertEquals(RelativeTime.JustNow, at(59_000L))
    }

    @Test
    fun `one minute to fifty-nine minutes`() {
        assertEquals(RelativeTime.Minutes(1), at(60_000L))
        assertEquals(RelativeTime.Minutes(12), at(12L * 60_000L))
        assertEquals(RelativeTime.Minutes(59), at(59L * 60_000L))
    }

    @Test
    fun `one hour to twenty-three hours`() {
        assertEquals(RelativeTime.Hours(1), at(3_600_000L))
        assertEquals(RelativeTime.Hours(5), at(5L * 3_600_000L))
        assertEquals(RelativeTime.Hours(23), at(23L * 3_600_000L))
    }

    @Test
    fun `twenty-four to forty-seven hours is yesterday`() {
        assertEquals(RelativeTime.Yesterday, at(24L * 3_600_000L))
        assertEquals(RelativeTime.Yesterday, at(47L * 3_600_000L))
    }

    @Test
    fun `forty-eight hours and beyond is day count`() {
        assertEquals(RelativeTime.Days(2), at(48L * 3_600_000L))
        assertEquals(RelativeTime.Days(5), at(5L * 86_400_000L))
    }

    @Test
    fun `future timestamps do not crash and read as just now`() {
        assertEquals(RelativeTime.JustNow, RelativeTime.compute(t0 + 5_000L, t0))
    }

    @Test
    fun `count of one uses the singular label resource`() {
        assertEquals(R.string.time_minute_ago, RelativeTime.labelRes(RelativeTime.Minutes(1)))
        assertEquals(R.string.time_hour_ago, RelativeTime.labelRes(RelativeTime.Hours(1)))
        assertEquals(R.string.time_day_ago, RelativeTime.labelRes(RelativeTime.Days(1)))
        assertFalse(RelativeTime.hasCountArg(RelativeTime.Minutes(1)))
        assertTrue(RelativeTime.hasCountArg(RelativeTime.Minutes(7)))
        assertFalse(RelativeTime.hasCountArg(RelativeTime.JustNow))
        assertFalse(RelativeTime.hasCountArg(RelativeTime.Yesterday))
    }

    @Test
    fun `elapsed duration clamps negative clock skew and picks buckets`() {
        // Server clock 40s AHEAD of device: elapsed must clamp to 0, never go negative.
        val (res0, args0) = RelativeTime.elapsedRes(t0 + 40_000L, t0)
        assertEquals(R.string.duration_sec, res0)
        assertEquals(0, args0[0])
        // 5 min
        val (res5, args5) = RelativeTime.elapsedRes(t0, t0 + 5L * 60_000L)
        assertEquals(R.string.duration_min, res5)
        assertEquals(5, args5[0])
        // 2 hr 3 min
        val (resH, argsH) = RelativeTime.elapsedRes(t0, t0 + 2L * 3_600_000L + 3L * 60_000L)
        assertEquals(R.string.duration_hr_min, resH)
        assertEquals(2, argsH[0])
        assertEquals(3, argsH[1])
    }
}
