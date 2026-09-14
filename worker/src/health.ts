/**
 * MVP-015: connection health. The worker records the activity it actually
 * observes per (channel, source) — accepted publishes, GitHub webhooks,
 * device syncs — and derives a small, honest state machine:
 *
 *   CONNECTED   seen in the last 24h
 *   DEGRADED    seen 24–72h ago (may be fine, may not be — shown as "check")
 *   UNKNOWN     never seen (rare-event sources must not read as "broken")
 *   DISCONNECTED reserved for explicit disconnects (connector token revoked)
 *
 * Internal reasons (HTTP codes, token errors) are stored but only surfaced
 * through the detail screen, never in the one-line status copy.
 */

import type { Env } from "./types.js";

export const CONNECTED_MS = 24 * 3600_000;
export const DEGRADED_MS = 72 * 3600_000;

export type HealthState = "CONNECTED" | "DEGRADED" | "DISCONNECTED" | "UNKNOWN";

export interface HealthRow {
  source: string;
  last_seen_at: string | null;
  last_event_at: string | null;
  last_success_at: string | null;
  last_failure_at: string | null;
  last_failure_reason: string | null;
}

export function deriveState(row: HealthRow, now = Date.now()): HealthState {
  if (!row.last_seen_at) return "UNKNOWN";
  const age = now - new Date(row.last_seen_at).getTime();
  if (age <= CONNECTED_MS) return "CONNECTED";
  if (age <= DEGRADED_MS) return "DEGRADED";
  return "DEGRADED";
}

export async function recordConnectorSeen(
  env: Env,
  channelId: string,
  source: string,
  opts: { event?: boolean; success?: boolean; failureReason?: string } = {},
): Promise<void> {
  const now = new Date().toISOString();
  const record = opts.failureReason
    ? { lastFailureAt: now, lastFailureReason: opts.failureReason.slice(0, 300) }
    : opts.success === false
      ? {}
      : { lastSuccessAt: now };
  await env.DB.prepare(
    `INSERT INTO connector_health
       (channel_id, source, last_seen_at, last_event_at, last_success_at, last_failure_at, last_failure_reason)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
     ON CONFLICT (channel_id, source) DO UPDATE SET
       last_seen_at = ?3,
       last_event_at = COALESCE(?4, last_event_at),
       last_success_at = COALESCE(?5, last_success_at),
       last_failure_at = COALESCE(?6, last_failure_at),
       last_failure_reason = COALESCE(?7, last_failure_reason)`,
  )
    .bind(
      channelId,
      source,
      now,
      opts.event ? now : null,
      record.lastSuccessAt ?? null,
      record.lastFailureAt ?? null,
      record.lastFailureReason ?? null,
    )
    .run();
}

export async function getChannelHealth(env: Env, channelId: string): Promise<HealthRow[]> {
  const rows = await env.DB.prepare(
    `SELECT source, last_seen_at, last_event_at, last_success_at, last_failure_at, last_failure_reason
     FROM connector_health WHERE channel_id = ?1 ORDER BY source`,
  )
    .bind(channelId)
    .all<HealthRow>();
  return rows.results ?? [];
}
