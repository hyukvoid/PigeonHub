import type { Env } from "./types.js";

/**
 * MVP-003A — Canonical Agent Event v1.
 *
 * Agent events are OPTIONAL extensions to the existing generic message.
 * A generic {title, message} body works exactly as before. When the body
 * contains an `agent_event` object, it is validated and stored as structured
 * metadata alongside the message.
 *
 * Privacy: only allowlist-oriented short structured fields are accepted.
 * Full prompts, transcripts, chain-of-thought, env vars, credentials, and
 * arbitrary terminal output are structurally rejected by size limits.
 */

export const MAX_SUMMARY = 500;
export const MAX_FACTS = 10;
export const MAX_FACT_KEY = 64;
export const MAX_FACT_VALUE = 200;
export const MAX_PROVIDER = 64;
export const MAX_RUN_ID = 128;
export const MAX_EVENT_ID = 128;
export const MAX_PROJECT = 128;
export const MAX_TASK = 256;
export const MAX_ATTENTION_REASON_LENGTH = 32;

export interface AgentEvent {
  eventId: string;
  eventType: string;
  provider: string;
  runId: string;
  project?: string;
  task?: string;
  attentionReason?: string;
  summary?: string;
  facts?: Record<string, string>;
  actionUrl?: string;
}

const EVENT_TYPES = new Set([
  "agent.attention_required",
  "agent.attention_resolved",
  "agent.finished",
  "agent.blocked",
  "agent.started",
]);

const ATTENTION_REASONS = new Set([
  "input",
  "approval",
  "permission",
  "clarification",
  "other",
]);

const BLOCKED_REASONS = new Set([
  "rate_limit",
  "quota",
  "auth",
  "environment",
  "tool_failure",
  "other",
]);

export interface ValidatedAgentEvent {
  eventId: string;
  eventType: string;
  provider: string;
  runId: string;
  project: string | null;
  task: string | null;
  attentionReason: string | null;
  summary: string | null;
  factsJson: string | null;
}

export function validateAgentEvent(
  raw: unknown,
): { ok: true; event: ValidatedAgentEvent } | { ok: false; errors: string[] } {
  const errors: string[] = [];
  if (typeof raw !== "object" || raw === null) {
    return { ok: false, errors: ["agent_event must be an object"] };
  }
  const obj = raw as Record<string, unknown>;

  const eventId = str(obj, "eventId", MAX_EVENT_ID);
  if (!eventId) errors.push("agent_event.eventId is required (1..128 chars)");
  const eventType = str(obj, "eventType", 64);
  if (!eventType || !EVENT_TYPES.has(eventType)) {
    errors.push(`agent_event.eventType must be one of: ${[...EVENT_TYPES].join(", ")}`);
  }
  const provider = str(obj, "provider", MAX_PROVIDER);
  if (!provider) errors.push("agent_event.provider is required (1..64 chars)");
  const runId = str(obj, "runId", MAX_RUN_ID);
  if (!runId) errors.push("agent_event.runId is required (1..128 chars)");

  const project = optStr(obj, "project", MAX_PROJECT);
  const task = optStr(obj, "task", MAX_TASK);
  const summary = optStr(obj, "summary", MAX_SUMMARY);

  let attentionReason: string | null = null;
  if (eventType === "agent.attention_required" || eventType === "agent.blocked") {
    const reason = str(obj, "attentionReason", MAX_ATTENTION_REASON_LENGTH);
    if (!reason) {
      errors.push("agent_event.attentionReason is required for attention_required/blocked");
    } else if (eventType === "agent.attention_required" && !ATTENTION_REASONS.has(reason)) {
      errors.push(`attentionReason must be one of: ${[...ATTENTION_REASONS].join(", ")}`);
    } else if (eventType === "agent.blocked" && !BLOCKED_REASONS.has(reason)) {
      errors.push(`blocked attentionReason must be one of: ${[...BLOCKED_REASONS].join(", ")}`);
    }
    attentionReason = reason;
  }

  let factsJson: string | null = null;
  if (obj.facts !== undefined && obj.facts !== null) {
    if (typeof obj.facts !== "object" || Array.isArray(obj.facts)) {
      errors.push("agent_event.facts must be an object");
    } else {
      const entries = Object.entries(obj.facts as Record<string, unknown>);
      if (entries.length > MAX_FACTS) {
        errors.push(`agent_event.facts must have at most ${MAX_FACTS} entries`);
      } else {
        const cleaned: Record<string, string> = {};
        for (const [k, v] of entries) {
          if (typeof k !== "string" || k.length > MAX_FACT_KEY) {
            errors.push(`fact key too long (max ${MAX_FACT_KEY}): ${k.slice(0, 20)}`);
            continue;
          }
          const vs = typeof v === "string" ? v : String(v);
          if (vs.length > MAX_FACT_VALUE) {
            errors.push(`fact value too long (max ${MAX_FACT_VALUE}): ${k}`);
            continue;
          }
          cleaned[k.slice(0, MAX_FACT_KEY)] = vs.slice(0, MAX_FACT_VALUE);
        }
        if (Object.keys(cleaned).length > 0 && errors.length === 0) {
          factsJson = JSON.stringify(cleaned);
        }
      }
    }
  }

  if (errors.length > 0) return { ok: false, errors };

  return {
    ok: true,
    event: {
      eventId,
      eventType,
      provider,
      runId,
      project: project || null,
      task: task || null,
      attentionReason,
      summary: summary || null,
      factsJson,
    },
  };
}

function str(obj: Record<string, unknown>, key: string, max: number): string {
  const v = obj[key];
  if (typeof v !== "string") return "";
  return v.trim().slice(0, max);
}

function optStr(obj: Record<string, unknown>, key: string, max: number): string {
  const v = obj[key];
  if (typeof v !== "string") return "";
  return v.trim().slice(0, max);
}

/** Attention-worthy blocked reasons (user action likely helps). */
export function isAttentionWorthy(reason: string): boolean {
  return ["auth", "quota", "environment", "tool_failure", "input", "approval", "permission", "clarification"].includes(reason);
}
