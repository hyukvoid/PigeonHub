package com.pigeonhub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pigeonhub.app.R
import com.pigeonhub.app.push.installation.HealthApi
import java.time.OffsetDateTime

/**
 * MVP-015: one honest line under a Connections card — a state dot plus the
 * last observed activity in relative time. Copy never exposes internal terms
 * (webhook / token / FCM / HTTP); failures are summarized only.
 */
@Composable
fun HealthLine(
    health: HealthApi.ConnectorHealth?,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    if (health == null || health.state == HealthApi.State.UNKNOWN) return
    val color = when (health.state) {
        HealthApi.State.CONNECTED -> MaterialTheme.colorScheme.primary
        HealthApi.State.DEGRADED -> MaterialTheme.colorScheme.tertiary
        HealthApi.State.DISCONNECTED -> MaterialTheme.colorScheme.error
        HealthApi.State.UNKNOWN -> return
    }
    val stateLabel = when (health.state) {
        HealthApi.State.CONNECTED -> stringResource(R.string.health_connected)
        HealthApi.State.DEGRADED -> stringResource(R.string.health_degraded)
        HealthApi.State.DISCONNECTED -> stringResource(R.string.health_disconnected)
        HealthApi.State.UNKNOWN -> return
    }
    val lastEvent = health.lastEventAt ?: health.lastSeenAt
    val activity = lastEvent?.let {
        runCatching {
            relativeLabel(OffsetDateTime.parse(it).toInstant().toEpochMilli(), nowMs)
        }.getOrNull()
    }
    val summary = if (activity != null) {
        stringResource(R.string.health_last_event, activity)
    } else {
        stateLabel
    }
    val line = if (health.state == HealthApi.State.CONNECTED) {
        summary
    } else {
        "$stateLabel · $summary"
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = stateLabel },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            line,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
