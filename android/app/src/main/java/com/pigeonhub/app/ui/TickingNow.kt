package com.pigeonhub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * MVP-011.5A: the current wall clock, refreshed every [intervalMs] while the
 * host is RESUMED and immediately on background->foreground return. Anything
 * age-derived (relative labels, health lines) renders from THIS value plus a
 * stored timestamp, so labels can never freeze.
 */
@Composable
fun rememberTickingNow(intervalMs: Long = 60_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = System.currentTimeMillis()
            while (true) {
                delay(intervalMs)
                now = System.currentTimeMillis()
            }
        }
    }
    return now
}

/** Shared relative-label formatter: picks singular/plural resources. */
@Composable
internal fun relativeLabel(timestampMs: Long, nowMs: Long): String {
    val time = RelativeTime.compute(timestampMs, nowMs)
    val res = RelativeTime.labelRes(time)
    return if (RelativeTime.hasCountArg(time)) {
        androidx.compose.ui.res.stringResource(res, RelativeTime.countArg(time))
    } else {
        androidx.compose.ui.res.stringResource(res)
    }
}
