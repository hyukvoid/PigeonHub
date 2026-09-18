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
            is Minutes -> if (time.count == 1) R.string.time_minute_ago else R.string.time_minutes_ago
            is Hours -> if (time.count == 1) R.string.time_hour_ago else R.string.time_hours_ago
            Yesterday -> R.string.time_yesterday
            is Days -> if (time.count == 1) R.string.time_day_ago else R.string.time_days_ago
        }

        /** Whether [labelRes]'s string needs the count format argument. */
        fun hasCountArg(time: RelativeTime): Boolean = when (time) {
            is Minutes -> time.count > 1
            is Hours -> time.count > 1
            is Days -> time.count > 1
            else -> false
        }

        fun countArg(time: RelativeTime): Int = when (time) {
            is Minutes -> time.count
            is Hours -> time.count
            is Days -> time.count
            else -> 0
        }

        /**
         * Elapsed-duration label for a running job: res id + format args,
         * CLAMPED at zero — started_at carries the connector's clock, which
         * can be ahead of the device, and a negative elapsed must never
         * render as a future form ("in 40 sec").
         */
        fun elapsedRes(startedMs: Long, nowMs: Long): Pair<Int, IntArray> {
            val s = ((nowMs - startedMs) / 1000L).coerceAtLeast(0L)
            return when {
                s < 60L -> R.string.duration_sec to intArrayOf(s.toInt())
                s < 3600L -> R.string.duration_min to intArrayOf((s / 60L).toInt())
                else -> R.string.duration_hr_min to intArrayOf(
                    (s / 3600L).toInt(),
                    ((s % 3600L) / 60L).toInt(),
                )
            }
        }

        /**
         * MVP-019 soft staleness marker: a RUNNING job whose last event is
         * older than [thresholdMs] gets an honest "no updates for X" line —
         * never a fabricated terminal state, never a push. Returns null while
         * the job is fresh.
         */
        fun staleNoUpdatesRes(lastEventMs: Long, nowMs: Long, thresholdMs: Long): Pair<Int, IntArray>? {
            val diff = (nowMs - lastEventMs).coerceAtLeast(0L)
            if (diff < thresholdMs) return null
            val minutes = diff / MINUTE_MS
            return when {
                minutes < 60L -> R.string.duration_min to intArrayOf(minutes.toInt())
                minutes < 24L * 60L -> R.string.duration_hr_min to intArrayOf(
                    (minutes / 60L).toInt(),
                    (minutes % 60L).toInt(),
                )
                else -> R.string.duration_day to intArrayOf((minutes / (24L * 60L)).toInt())
            }
        }
    }
}
