package com.pigeonhub.app.ui

import com.pigeonhub.app.R

/**
 * MVP-011.5A: relative time label computed from the message timestamp and the
 * CURRENT time — never stored, never frozen. Pure function so the day
 * boundaries are unit-testable with a fake clock.
 *
 * Buckets: <1m just-now, <60m minutes, <24h hours, <48h yesterday, else days.
 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val count: Int) : RelativeTime

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L

        fun compute(timestampMs: Long, nowMs: Long): RelativeTime {
            val diff = (nowMs - timestampMs).coerceAtLeast(0L)
            if (diff < MINUTE_MS) return JustNow
            val minutes = diff / MINUTE_MS
            if (minutes < 60) return Minutes(minutes.toInt())
            val hours = diff / HOUR_MS
            if (hours < 24) return Hours(hours.toInt())
            if (hours < 48) return Yesterday
            return Days((hours / 24).toInt())
        }

        fun labelRes(time: RelativeTime): Int = when (time) {
            JustNow -> R.string.time_just_now
            is Minutes -> R.string.time_minutes_ago
            is Hours -> R.string.time_hours_ago
            Yesterday -> R.string.time_yesterday
            is Days -> R.string.time_days_ago
        }

        fun countArg(time: RelativeTime): Int = when (time) {
            is Minutes -> time.count
            is Hours -> time.count
            is Days -> time.count
            else -> 0
        }
    }
}
