import { getAccessToken } from "./gcp_auth.js";
import { decryptFcmToken } from "./bootstrap.js";
import { sendToFcm } from "./fcm.js";
import type { Env, ResolvedPush } from "./types.js";

/**
 * MVP-001E — Bounded Delivery Recovery & Beta Gates.
 *
 * Two bounded maintenance jobs, run by the Cron trigger (every 15 min) and
 * manually via the bearer-gated POST /v1/maintenance/run:
 *
 * 1. PENDING RETRY — pending messages whose FCM was never accepted are retried
 *    with hard bounds: max 5 attempts, age-based backoff (attempt_count × 30
 *    min), a 100/day retry ceiling, and a 10-message batch per run. Rows whose
 *    fcm_message_id is already set (crash between FCM accept and the state
 *    update — MVP-001B scenario D) are SELF-HEALED to fcm_accepted without a
 *    second send.
 *
 * 2. RETENTION CLEANUP — expired messages (expires_at < now) are deleted in a
 *    bounded batch and each affected channel's retention_floor_seq advances to
 *    the highest deleted seq, driving the history_truncated sync contract.
 *    Sequence gaps after deletion are expected retention artifacts.
 */

const MAX_ATTEMPTS = 5;
const RETRY_BATCH = 10;
const RETRY_BACKOFF_MS = 30 * 60 * 1000; // attempt_count × 30 min
const RETRY_DAILY_CEILING = 100;
const CLEANUP_BATCH = 200;

export interface MaintenanceSummary {
  retried: number;
  retryAccepted: number;
  retryFailedPermanent: number;
  retryStillPending: number;
  selfHealedScenarioD: number;
  retryCeilingHit: boolean;
  expiredDeleted: number;
  floorsAdvanced: { channelId: string; floor: number }[];
}

export async function runMaintenance(env: Env): Promise<MaintenanceSummary> {
  const summary: MaintenanceSummary = {
    retried: 0,
    retryAccepted: 0,
    retryFailedPermanent: 0,
    retryStillPending: 0,
    selfHealedScenarioD: 0,
    retryCeilingHit: false,
    expiredDeleted: 0,
    floorsAdvanced: [],
  };

  // ---------- 1a. self-heal scenario D (FCM accepted, update crashed) ----------
  const healed = await env.DB.prepare(
    `UPDATE messages SET push_status = 'fcm_accepted'
     WHERE push_status = 'pending' AND fcm_message_id IS NOT NULL AND attempt_count < ?1`,
  )
    .bind(MAX_ATTEMPTS)
    .run();
  summary.selfHealedScenarioD = healed.meta.changes ?? 0;

  // ---------- 1b. bounded daily retry ceiling (free-tier protection) ----------
  const today = new Date().toISOString().slice(0, 10);
  const ceiling = await env.DB.prepare(
    `INSERT INTO quota_buckets (scope, bucket_start, accepted_count)
     VALUES ('cron|retry', ?1, 1)
     ON CONFLICT(scope, bucket_start) DO UPDATE
       SET accepted_count = accepted_count + 1
       WHERE accepted_count < ?2
     RETURNING accepted_count`,
  )
    .bind(today, RETRY_DAILY_CEILING)
    .first<{ accepted_count: number }>();
  if (ceiling === null) {
    summary.retryCeilingHit = true;
    return summary;
  }

  // ---------- 1c. bounded retry batch with age-based backoff ----------
  const retryableAfter = new Date(Date.now() - RETRY_BACKOFF_MS).toISOString();
  const pending = await env.DB.prepare(
    `SELECT m.id, m.channel_id, m.seq, m.title, m.message, m.priority, m.url,
            m.created_at, m.updated_at, i.fcm_token_ciphertext, i.fcm_token_nonce
     FROM messages m
     JOIN channels c ON c.id = m.channel_id
     JOIN installations i ON i.id = c.installation_id
     WHERE m.push_status = 'pending'
       AND m.attempt_count >= 1
       AND m.attempt_count < ?1
       AND m.fcm_message_id IS NULL
       AND i.enabled = 1
       AND m.updated_at < ?2
     ORDER BY m.seq ASC
     LIMIT ?3`,
  )
    .bind(MAX_ATTEMPTS, retryableAfter, RETRY_BATCH)
    .all<{
      id: string;
      channel_id: string;
      seq: number;
      title: string;
      message: string;
      priority: string;
      url: string | null;
      created_at: string;
      updated_at: string;
      fcm_token_ciphertext: string;
      fcm_token_nonce: string;
    }>();

  for (const row of pending.results ?? []) {
    summary.retried++;
    let targetToken: string;
    try {
      targetToken = await decryptFcmToken(env, row.fcm_token_ciphertext, row.fcm_token_nonce);
    } catch {
      await markRetryOutcome(env, row.id, "failed", null, "retry skipped: token decryption failed");
      summary.retryFailedPermanent++;
      continue;
    }
    const push: ResolvedPush = {
      message_id: row.id,
      title: row.title,
      message: row.message,
      priority: row.priority === "high" ? "high" : "normal",
      url: row.url ?? undefined,
      sent_at: row.created_at,
      schema_version: "1",
      targetToken,
    };
    const sent = await sendToFcm(env, push, targetToken, row.channel_id, row.seq);
    if (sent.ok) {
      await markRetryOutcome(env, row.id, "fcm_accepted", sent.fcmMessageId ?? null, null);
      summary.retryAccepted++;
    } else if (sent.transient) {
      await markRetryOutcome(env, row.id, "pending", null, sent.detail ?? "retry failed");
      summary.retryStillPending++;
    } else {
      await markRetryOutcome(env, row.id, "failed", null, sent.detail ?? "retry failed");
      summary.retryFailedPermanent++;
    }
  }

  // ---------- 2. bounded retention cleanup + watermark advancement ----------
  const nowIso = new Date().toISOString();
  const expired = await env.DB.prepare(
    `SELECT id, channel_id, seq FROM messages
     WHERE expires_at < ?1 ORDER BY seq ASC LIMIT ?2`,
  )
    .bind(nowIso, CLEANUP_BATCH)
    .all<{ id: string; channel_id: string; seq: number }>();

  if (expired.results.length > 0) {
    const floorByChannel = new Map<string, number>();
    for (const row of expired.results) {
      floorByChannel.set(row.channel_id, Math.max(floorByChannel.get(row.channel_id) ?? 0, row.seq));
    }
    const ids = expired.results.map((r) => r.id);
    const placeholders = ids.map((_, i) => `?${i + 1}`).join(",");
    await env.DB.prepare(`DELETE FROM messages WHERE id IN (${placeholders})`).bind(...ids).run();
    summary.expiredDeleted = ids.length;
    for (const [channelId, floor] of floorByChannel) {
      await env.DB.prepare(
        `UPDATE channels SET retention_floor_seq = MAX(retention_floor_seq, ?2), updated_at = ?3
         WHERE id = ?1`,
      )
        .bind(channelId, floor, new Date().toISOString())
        .run();
      summary.floorsAdvanced.push({ channelId, floor });
    }
  }

  return summary;
}

async function markRetryOutcome(
  env: Env,
  id: string,
  status: "fcm_accepted" | "pending" | "failed",
  fcmMessageId: string | null,
  lastError: string | null,
): Promise<void> {
  await env.DB.prepare(
    `UPDATE messages SET push_status = ?2, attempt_count = attempt_count + 1,
       fcm_message_id = COALESCE(?3, fcm_message_id), last_error = ?4, updated_at = ?5
     WHERE id = ?1`,
  )
    .bind(id, status, fcmMessageId, lastError, new Date().toISOString())
    .run();
}

/** Beta gate: rejects fresh registrations once the installation count reaches the cap. */
export async function betaCapacityReached(env: Env): Promise<boolean> {
  const cap = Number(env.BETA_MAX_INSTALLATIONS ?? "20");
  if (!Number.isFinite(cap) || cap <= 0) return false;
  const row = await env.DB.prepare(`SELECT COUNT(*) AS n FROM installations WHERE enabled = 1`)
    .first<{ n: number }>();
  return (row?.n ?? 0) >= cap;
}
