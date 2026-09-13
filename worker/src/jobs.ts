/**
 * MVP-005: normalized Job Event layer.
 *
 * Connectors never build their own FCM logic — they publish through the
 * existing channel API with an optional `job` object; this module validates it
 * into a canonical shape that travels with the message to D1, FCM and sync.
 * States: RUNNING / PROGRESS / DONE / FAILED / NEEDS_ACTION (PROGRESS is an
 * event state; the phone renders it as a RUNNING job with a progress counter).
 */

export const JOB_STATES = ["RUNNING", "PROGRESS", "DONE", "FAILED", "NEEDS_ACTION"] as const;

export type JobState = (typeof JOB_STATES)[number];

export interface JobMeta {
  source: string;
  job_id: string;
  job_name: string | null;
  state: JobState;
  started_at: string | null;
  finished_at: string | null;
  progress_current: number | null;
  progress_total: number | null;
  attention_reason: string | null;
  result_summary: string | null;
  deep_link: string | null;
}

export type ValidatedJobEvent = JobMeta;

function optionalString(value: unknown, max: number): string | null | undefined {
  // undefined = field absent; null = explicitly cleared; string = trimmed value
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, max) : null;
}

function optionalInt(value: unknown): number | null | undefined {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (typeof value === "number" && Number.isInteger(value) && value >= 0) return value;
  return null;
}

function optionalTimestamp(value: unknown, errors: string[], label: string): string | null | undefined {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (typeof value !== "string" || !value.trim()) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    errors.push(`${label} must be an ISO-8601 timestamp`);
    return null;
  }
  return value.trim();
}

export function validateJobEvent(
  value: unknown,
): { ok: true; job: JobMeta } | { ok: false; errors: string[] } {
  const errors: string[] = [];
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return { ok: false, errors: ["job must be an object"] };
  }
  const job = value as Record<string, unknown>;

  const source = optionalString(job.source, 32) ?? "";
  if (!source) errors.push("job.source is required (string, <=32 chars)");
  else if (!/^[a-z0-9_-]+$/.test(source)) {
    errors.push("job.source must match [a-z0-9_-]");
  }

  const jobId = optionalString(job.job_id, 128) ?? "";
  if (!jobId) errors.push("job.job_id is required (string, <=128 chars)");

  const state = typeof job.state === "string" ? job.state.trim().toUpperCase() : "";
  if (!JOB_STATES.includes(state as JobState)) {
    errors.push(`job.state must be one of ${JOB_STATES.join("|")}`);
  }

  for (const label of ["started_at", "finished_at"] as const) {
    optionalTimestamp(job[label], errors, `job.${label}`);
  }

  const progressCurrent = optionalInt(job.progress_current);
  if (job.progress_current !== undefined && job.progress_current !== null && progressCurrent === null) {
    errors.push("job.progress_current must be a non-negative integer");
  }
  const progressTotal = optionalInt(job.progress_total);
  if (job.progress_total !== undefined && job.progress_total !== null && progressTotal === null) {
    errors.push("job.progress_total must be a non-negative integer");
  }

  if (errors.length > 0) return { ok: false, errors };

  const deepLinkRaw = optionalString(job.deep_link, 2000);
  let deepLink: string | null = null;
  if (deepLinkRaw) {
    try {
      const parsed = new URL(deepLinkRaw);
      if (parsed.protocol !== "https:") errors.push("job.deep_link must be https");
      else deepLink = deepLinkRaw;
    } catch {
      errors.push("job.deep_link must be an absolute https URL");
    }
  }
  if (errors.length > 0) return { ok: false, errors };

  return {
    ok: true,
    job: {
      source,
      job_id: jobId,
      job_name: optionalString(job.job_name, 200) ?? null,
      state: state as JobState,
      started_at: optionalTimestamp(job.started_at, errors, "job.started_at") ?? null,
      finished_at: optionalTimestamp(job.finished_at, errors, "job.finished_at") ?? null,
      progress_current: progressCurrent ?? null,
      progress_total: progressTotal ?? null,
      attention_reason: optionalString(job.attention_reason, 300) ?? null,
      result_summary: optionalString(job.result_summary, 300) ?? null,
      deep_link: deepLink,
    },
  };
}

/** FCM data-payload projection: everything is a string, absent fields omitted. */
export function jobFcmData(job: JobMeta | null | undefined): Record<string, string> {
  if (!job) return {};
  const data: Record<string, string> = {
    job_source: job.source,
    job_id: job.job_id,
    job_state: job.state,
  };
  if (job.job_name) data.job_name = job.job_name;
  if (job.started_at) data.job_started_at = job.started_at;
  if (job.finished_at) data.job_finished_at = job.finished_at;
  if (job.progress_current !== null) data.job_progress_current = String(job.progress_current);
  if (job.progress_total !== null) data.job_progress_total = String(job.progress_total);
  if (job.attention_reason) data.job_attention_reason = job.attention_reason;
  if (job.result_summary) data.job_result_summary = job.result_summary;
  if (job.deep_link) data.job_deep_link = job.deep_link;
  return data;
}
