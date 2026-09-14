package com.pigeonhub.app.ui

import org.junit.Assert.assertEquals
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
}
