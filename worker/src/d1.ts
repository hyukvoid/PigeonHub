import type { Env, ResolvedPush, StoredMessage } from "./types.js";
import type { JobMeta } from "./jobs.js";

/**
 * D1 durable message core (MVP-001B).
 *
 * Contract: a message is persisted as `pending` BEFORE any FCM call. Whatever
 * happens afterwards (FCM error, status-update crash), the accepted message
 * stays in D1 and its state is updated in place.
 *
 * State machine: pending -> fcm_accepted | failed.
 * ("delivered" is deliberately not a state — FCM accept is not a device ack.)
 */

export const MESSAGES_TTL_DAYS = 7;

/** Canonical hash: field order can never change the digest. */
export function canonicalRequestHash(
  title: string,
  message: string,
  priority: string,
  url: string | null,
): Promise<string> {
  const canonical = JSON.stringify({
    message,
    priority,
    title,
    url: url ?? null, // missing and empty url normalize to the same digest
  });
  return crypto.subtle
    .digest("SHA-256", new TextEncoder().encode(canonical))
    .then((buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join(""));
}

function bucketStart(kind: "daily" | "minute", now = new Date()): string {
  const iso = now.toISOString();
  return kind === "daily" ? iso.slice(0, 10) : iso.slice(0, 16);
}

function quotaLimits(env: Env): { minute: number; daily: number; globalDaily: number } {
  return {
    minute: Number(env.QUOTA_MINUTE_LIMIT ?? "5"),
    daily: Number(env.QUOTA_DAILY_LIMIT ?? "50"),
    globalDaily: Number(env.GLOBAL_DAILY_LIMIT ?? "1000"),
  };
}

/**
 * Atomic, guarded consumption: the counter only increments while it is below
 * the limit, so concurrent requests can never push acceptance past the limit.
 * Returns the new count, or null when the bucket is exhausted.
 */
async function consumeQuota(
  scope: string,
  bucket: string,
  limit: number,
  db: D1Database,
): Promise<number | null> {
  const result = await db.prepare(
    `INSERT INTO quota_buckets (scope, bucket_start, accepted_count)
     VALUES (?1, ?2, 1)
     ON CONFLICT(scope, bucket_start) DO UPDATE
       SET accepted_count = accepted_count + 1
       WHERE accepted_count < ?3
     RETURNING accepted_count`,
  )
    .bind(scope, bucket, limit)
    .first<{ accepted_count: number }>();
  return result ? result.accepted_count : null;
}

/** Returns a consumed quota slot (used when a racing insert loses idempotency). */
export async function refundQuota(env: Env, scopeBase: string): Promise<void> {
  await env.DB.batch([
    env.DB.prepare(
      `UPDATE quota_buckets SET accepted_count = MAX(accepted_count - 1, 0)
       WHERE scope = ?1 AND bucket_start = ?2`,
    ).bind(`${scopeBase}|minute`, bucketStart("minute")),
    env.DB.prepare(
      `UPDATE quota_buckets SET accepted_count = MAX(accepted_count - 1, 0)
       WHERE scope = ?1 AND bucket_start = ?2`,
    ).bind(`${scopeBase}|daily`, bucketStart("daily")),
  ]);
}

/** 429 when the minute, per-scope daily, or global daily bucket is exhausted. */
export async function enforceQuota(env: Env, scopeBase: string): Promise<void> {
  const limits = quotaLimits(env);
  if (
    (await consumeQuota(`${scopeBase}|minute`, bucketStart("minute"), limits.minute, env.DB)) ===
    null
  ) {
    throw new Error("quota:minute");
  }
  if (
    (await consumeQuota(`${scopeBase}|daily`, bucketStart("daily"), limits.daily, env.DB)) === null
  ) {
    throw new Error("quota:daily");
  }
  if ((await consumeQuota("global|daily", bucketStart("daily"), limits.globalDaily, env.DB)) === null) {
    throw new Error("quota:global");
  }
}

export interface IdempotencyLookup {
  replay: StoredMessage | null;
  conflict: boolean;
}

export async function lookupIdempotency(
  env: Env,
  channelId: string,
  key: string,
  requestHash: string,
): Promise<IdempotencyLookup> {
  const row = await env.DB.prepare(
    `SELECT id, seq, push_status, request_hash, fcm_message_id, last_error
     FROM messages WHERE channel_id = ?1 AND idempotency_key = ?2`,
  )
    .bind(channelId, key)
    .first<StoredMessage>();
  if (!row) return { replay: null, conflict: false };
  return { replay: row, conflict: row.request_hash !== requestHash };
}

export async function getMessage(env: Env, id: string): Promise<StoredMessage | null> {
  return env.DB.prepare(
    `SELECT id, seq, push_status, request_hash, fcm_message_id, last_error
     FROM messages WHERE id = ?1`,
  )
    .bind(id)
    .first<StoredMessage>();
}

export class D1UniqueRace extends Error {
  constructor(readonly indexName: string) {
    super(`unique race on ${indexName}`);
  }
}

/**
 * D1 reports UNIQUE violations either by index name or by column list
 * ("UNIQUE constraint failed: messages.channel_id, messages.idempotency_key"),
 * so both shapes are matched.
 */
function classifyUniqueRace(text: string): "idem" | "seq" | null {
  if (/idx_messages_channel_idem|messages\.channel_id,\s*messages\.idempotency_key/.test(text)) {
    return "idem";
  }
  if (/idx_messages_channel_seq|messages\.channel_id,\s*messages\.seq\b/.test(text)) {
    return "seq";
  }
  return null;
}

export interface AgentMeta {
  eventType: string;
  provider: string;
  runId: string;
  eventId: string;
  attentionReason: string | null;
  factsJson: string | null;
}

/** Insert as `pending`. seq = channel MAX(seq)+1 allocated inside the statement. */
export async function insertPendingMessage(
  env: Env,
  channelId: string,
  push: ResolvedPush,
  requestHash: string,
  idempotencyKey: string | null,
  agent?: AgentMeta | null,
  job?: JobMeta | null,
): Promise<number> {
  const now = new Date();
  const expiresAt = new Date(now.getTime() + MESSAGES_TTL_DAYS * 86_400_000).toISOString();
  const id = push.message_id;
  try {
    await env.DB.prepare(
      `INSERT INTO messages
         (id, channel_id, seq, title, message, priority, url,
          created_at, expires_at, idempotency_key, request_hash, push_status, attempt_count,
          event_type, provider, run_id, event_id, attention_reason, facts_json,
          job_source, job_id, job_name, job_state, job_started_at, job_finished_at,
          job_progress_current, job_progress_total, job_attention_reason,
          job_result_summary, job_deep_link)
       SELECT ?1, ?2,
              COALESCE((SELECT MAX(seq) FROM messages WHERE channel_id = ?2), 0) + 1,
              ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, 'pending', 0, ?11, ?12, ?13, ?14, ?15, ?16,
              ?17, ?18, ?19, ?20, ?21, ?22, ?23, ?24, ?25, ?26, ?27`,
    )
      .bind(
        id,
        channelId,
        push.title,
        push.message,
        push.priority,
        push.url ?? null,
        now.toISOString(),
        expiresAt,
        idempotencyKey,
        requestHash,
      )
.bind(
        id,
        channelId,
        push.title,
        push.message,
        push.priority,
        push.url ?? null,
        now.toISOString(),
        expiresAt,
        idempotencyKey,
        requestHash,
        agent?.eventType ?? null,
        agent?.provider ?? null,
        agent?.runId ?? null,
        agent?.eventId ?? null,
        agent?.attentionReason ?? null,
        agent?.factsJson ?? null,
        job?.source ?? null,
        job?.job_id ?? null,
        job?.job_name ?? null,
        job?.state ?? null,
        job?.started_at ?? null,
        job?.finished_at ?? null,
        job?.progress_current ?? null,
        job?.progress_total ?? null,
        job?.attention_reason ?? null,
        job?.result_summary ?? null,
        job?.deep_link ?? null,
      )
      .run();
  } catch (error) {
    const text = error instanceof Error ? error.message : String(error);
    const race = classifyUniqueRace(text);
    if (race === "idem") throw new D1UniqueRace("channel_idem");
    if (race === "seq") throw new D1UniqueRace("channel_seq");
    throw error;
  }

  const row = await getMessage(env, id);
  if (!row) throw new Error("insert reported success but row is missing");
  return row.seq;
}

export async function updatePushStatus(
  env: Env,
  id: string,
  status: "fcm_accepted" | "pending" | "failed",
  fcmMessageId: string | null,
  lastError: string | null,
): Promise<void> {
  await env.DB.prepare(
    `UPDATE messages
     SET push_status = ?2, attempt_count = attempt_count + 1,
         fcm_message_id = COALESCE(?3, fcm_message_id),
         last_error = ?4
     WHERE id = ?1`,
  )
    .bind(id, status, fcmMessageId, lastError)
    .run();
}

/** Reliable identification of messages a future Cron/Queue retry would need. */
export async function countRetryablePending(env: Env): Promise<number> {
  const row = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM messages
     WHERE channel_id = ?1 AND push_status = 'pending'`,
  )
    .bind(env.CHANNEL_ID ?? "dev")
    .first<{ n: number }>();
  return row?.n ?? 0;
}
