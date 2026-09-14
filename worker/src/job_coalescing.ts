/**
 * MVP-011: server-side job progress coalescing.
 *
 * Policy (deterministic, per channel + source + job_id):
 *   RUNNING               → always emit (first event / re-run) and reset tracking
 *   DONE/FAILED/NEEDS_ACTION → always emit immediately, never delayed or dropped
 *   PROGRESS              → emit only when
 *       (a) >= 10s since the last EMITTED progress/running event, OR
 *       (b) the percentage advanced >= 5 points past the last emitted value, OR
 *       (c) the progress text/status materially changed (message or totals)
 *     otherwise the event is coalesced (overwritten) into the state row —
 *     no D1 message row, no quota consumption, no FCM send.
 *
 * Stale-progress protection: once a terminal state is recorded, a late
 * PROGRESS is dropped; the job can never flip back to RUNNING on the phone.
 * A fresh RUNNING after a terminal is a legitimate re-run: it emits and resets.
 *
 * All state lives in D1, so correctness survives worker restarts and
 * isolate eviction. Coalesced events cost exactly one state upsert — no
 * message row, no quota, no FCM.
 */

import type { Env } from "./types.js";
import type { ValidatedJobEvent } from "./jobs.js";

export type CoalesceDecision =
  | { emit: true }
  | { emit: false; reason: string };

const PROGRESS_MIN_INTERVAL_MS = 10_000;
const PROGRESS_MIN_PCT_POINTS = 5;

interface JobStateRow {
  last_state: string;
  last_emitted_at: string;
  last_message: string | null;
  last_current: number | null;
  last_total: number | null;
  pending_current: number | null;
  pending_total: number | null;
  pending_message: string | null;
}

function isTerminal(state: string): boolean {
  return state === "DONE" || state === "FAILED" || state === "NEEDS_ACTION";
}

function pct(current: number | null, total: number | null): number | null {
  if (current === null || total === null || total <= 0) return null;
  return (current / total) * 100;
}

export async function shouldEmitJobEvent(
  env: Env,
  channelId: string,
  job: ValidatedJobEvent,
  pushMessage: string,
): Promise<CoalesceDecision> {
  const now = Date.now();
  const nowIso = new Date(now).toISOString();
  const key = { channelId, source: job.source, jobId: job.job_id };

  // Non-progress events are never coalesced.
  if (job.state !== "PROGRESS") {
    if (job.state === "RUNNING") {
      await upsertState(env, key, {
        last_state: "RUNNING",
        last_emitted_at: nowIso,
        last_message: null,
        last_current: job.progress_current,
        last_total: job.progress_total,
        clearPending: true,
      });
      return { emit: true };
    }
    // Terminal: flush and record. Pending progress is superseded by the
    // terminal state (never delayed, never dropped).
    await upsertState(env, key, {
      last_state: job.state,
      last_emitted_at: nowIso,
      last_message: null,
      last_current: null,
      last_total: null,
      clearPending: true,
    });
    return { emit: true };
  }

  // ---- PROGRESS ----
  const row = await loadState(env, key);

  // Stale progress after a terminal event must not resurrect the job.
  if (row && isTerminal(row.last_state)) {
    return { emit: false, reason: "stale progress after terminal" };
  }

  if (!row) {
    // First visible event of the job: emit it as the running baseline.
    await upsertState(env, key, {
      last_state: "PROGRESS",
      last_emitted_at: nowIso,
      last_message: null,
      last_current: job.progress_current,
      last_total: job.progress_total,
      clearPending: true,
    });
    return { emit: true };
  }

  const lastEmittedAt = new Date(row.last_emitted_at).getTime();
  const intervalDue = now - lastEmittedAt >= PROGRESS_MIN_INTERVAL_MS;

  const lastPct = pct(row.last_current, row.last_total);
  const newPct = pct(job.progress_current, job.progress_total);
  const pctJump =
    lastPct !== null && newPct !== null && newPct - lastPct >= PROGRESS_MIN_PCT_POINTS;

  const totalsChanged =
    job.progress_total !== null && row.last_total !== null && job.progress_total !== row.last_total;
  const messageChanged = pushMessage !== row.last_message;

  const materialChange = pctJump || totalsChanged || messageChanged;

  if (intervalDue || materialChange) {
    await upsertState(env, key, {
      last_state: "PROGRESS",
      last_emitted_at: nowIso,
      last_message: pushMessage,
      last_current: job.progress_current,
      last_total: job.progress_total,
      clearPending: true,
    });
    return { emit: true };
  }

  // Coalesce: keep only the newest intermediate values (overwrite).
  await env.DB.prepare(
    `UPDATE job_states
     SET pending_current = ?4, pending_total = ?5, pending_message = ?6, updated_at = ?7
     WHERE channel_id = ?1 AND job_source = ?2 AND job_id = ?3`,
  )
    .bind(channelId, job.source, job.job_id, job.progress_current, job.progress_total, pushMessage, nowIso)
    .run();
  return { emit: false, reason: "progress coalesced" };
}

// ---------------------------------------------------------------------------
// D1 helpers

interface StateKey {
  channelId: string;
  source: string;
  jobId: string;
}

async function loadState(env: Env, key: StateKey): Promise<JobStateRow | null> {
  return env.DB.prepare(
    `SELECT last_state, last_emitted_at, last_message, last_current, last_total,
            pending_current, pending_total, pending_message
     FROM job_states WHERE channel_id = ?1 AND job_source = ?2 AND job_id = ?3`,
  )
    .bind(key.channelId, key.source, key.jobId)
    .first<JobStateRow>();
}

async function upsertState(
  env: Env,
  key: StateKey,
  values: {
    last_state: string;
    last_emitted_at: string;
    last_message: string | null;
    last_current: number | null;
    last_total: number | null;
    clearPending: boolean;
  },
): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO job_states
       (channel_id, job_source, job_id, last_state, last_emitted_at, last_message,
        last_current, last_total, pending_current, pending_total, pending_message, updated_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, NULL, NULL, NULL, ?9)
     ON CONFLICT(channel_id, job_source, job_id) DO UPDATE SET
       last_state = excluded.last_state,
       last_emitted_at = excluded.last_emitted_at,
       last_message = excluded.last_message,
       last_current = excluded.last_current,
       last_total = excluded.last_total,
       pending_current = NULL,
       pending_total = NULL,
       pending_message = NULL,
       updated_at = excluded.updated_at`,
  )
    .bind(
      key.channelId,
      key.source,
      key.jobId,
      values.last_state,
      values.last_emitted_at,
      values.last_message,
      values.last_current,
      values.last_total,
      values.last_emitted_at,
    )
    .run();
}
