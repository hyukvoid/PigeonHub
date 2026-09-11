import {
  canonicalRequestHash,
  D1UniqueRace,
  enforceQuota,
  insertPendingMessage,
  lookupIdempotency,
  refundQuota,
  updatePushStatus,
} from "./d1.js";
import { sendToFcm } from "./fcm.js";
import { validatePush } from "./validate.js";
import type { Env, PushRequest, ResolvedPush } from "./types.js";

/**
 * PigeonHub production transport (MVP-001A transport + MVP-001B durable core):
 *   POST /push
 *     1. bearer auth            5. quota acquire (atomic, guarded)
 *     2. payload validation     6. D1 insert as `pending`  ← BEFORE any FCM work
 *     3. idempotency check      7. FCM HTTP v1 (via OAuth2 access token)
 *     4. failure injection      8. push_status update → response
 *
 * Contract: `stored: true` means the message is in D1 regardless of what FCM
 * did afterwards. D1 insert failure ⇒ FCM is never called.
 * States: pending | fcm_accepted | failed ("delivered" is not a state — FCM
 * accept is not a device acknowledgement).
 */

function json(response: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(response), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function bearerMatches(request: Request, expected: string): boolean {
  const header = request.headers.get("Authorization") ?? "";
  const expectedHeader = `Bearer ${expected}`;
  if (header.length !== expectedHeader.length) return false;
  let diff = 0;
  for (let i = 0; i < header.length; i++) diff |= header.charCodeAt(i) ^ expectedHeader.charCodeAt(i);
  return diff === 0;
}

/** Returns a Response for replay/conflict, or null when publishing should proceed. */
async function lookup(
  env: Env,
  key: string,
  requestHash: string,
): Promise<Response | null> {
  const result = await lookupIdempotency(env, key, requestHash);
  if (result.conflict) {
    return json({ ok: false, error: "idempotency key reused with a different payload" }, 409);
  }
  if (result.replay) {
    return json({
      message_id: result.replay.id,
      stored: true,
      push_status: result.replay.push_status,
      seq: result.replay.seq,
      idempotent_replay: true,
    });
  }
  return null;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "pigeonhub-push-worker",
        project: env.FIREBASE_PROJECT_ID || "(unset)",
        durable: env.DB !== undefined,
        injection: env.FAILURE_INJECTION === "on",
      });
    }

    if (url.pathname === "/push") {
      if (request.method !== "POST") {
        return json({ ok: false, error: "method not allowed" }, 405);
      }
      if (!env.PUSH_BEARER_SECRET || !bearerMatches(request, env.PUSH_BEARER_SECRET)) {
        return json({ ok: false, error: "unauthorized" }, 401);
      }

      let body: PushRequest;
      try {
        body = (await request.json()) as PushRequest;
      } catch {
        return json({ ok: false, errors: ["body is not valid JSON"] }, 400);
      }

      const validated = validatePush(body, env.FCM_TEST_DEVICE_TOKEN);
      if (!validated.ok) {
        return json({ ok: false, errors: validated.errors }, 400);
      }
      const push: ResolvedPush = validated.push;

      const rawKey = request.headers.get("Idempotency-Key");
      let idempotencyKey: string | null = null;
      if (rawKey !== null) {
        const trimmed = rawKey.trim();
        if (!trimmed || trimmed.length > 200) {
          return json({ ok: false, errors: ["Idempotency-Key must be 1..200 characters"] }, 400);
        }
        idempotencyKey = trimmed;
      }

      const failAt =
        env.FAILURE_INJECTION === "on" ? request.headers.get("X-PigeonHub-Fail-At") : null;

      // ---- (A) failure injection BEFORE anything durable/external ----
      if (failAt === "d1_pre") {
        return json({ ok: false, stored: false, injected: "d1_pre" }, 500);
      }

      const requestHash = await canonicalRequestHash(
        push.title,
        push.message,
        push.priority,
        push.url ?? null,
      );

      // ---- idempotency check ----
      if (idempotencyKey !== null) {
        const early = await lookup(env, idempotencyKey, requestHash);
        if (early !== null) return early;
      }

      // ---- quota ----
      try {
        await enforceQuota(env);
      } catch (error) {
        if (error instanceof Error && error.message.startsWith("quota:")) {
          return json(
            { ok: false, stored: false, error: "quota exceeded", scope: error.message.slice(6) },
            429,
          );
        }
        throw error;
      }

      // ---- D1 insert (pending) ----
      let seq: number;
      try {
        seq = await insertPendingMessage(env, push, requestHash, idempotencyKey);
      } catch (error) {
        if (error instanceof D1UniqueRace) {
          if (error.indexName === "channel_idem" && idempotencyKey !== null) {
            // Lost an insert race against the same idempotency key:
            // converge on the winner and hand back the consumed quota slot.
            await refundQuota(env);
            const late = await lookup(env, idempotencyKey, requestHash);
            if (late !== null) return late;
          }
          // seq allocation raced (should be impossible: D1 serializes writes);
          // retry once with the unique index as the final backstop.
          seq = await insertPendingMessage(env, push, requestHash, idempotencyKey);
        } else {
          // Genuine D1 failure: nothing stored, so FCM must never run.
          return json(
            {
              ok: false,
              stored: false,
              error: "d1 insert failed",
              detail: error instanceof Error ? error.message : String(error),
            },
            500,
          );
        }
      }

      // ---- (B) failure injection AFTER the durable insert ----
      if (failAt === "d1_post") {
        return json(
          {
            ok: false,
            stored: true,
            injected: "d1_post",
            message_id: push.message_id,
            seq,
            push_status: "pending",
          },
          500,
        );
      }

      // ---- (C) failure injection at the FCM hop ----
      if (failAt === "fcm") {
        await updatePushStatus(env, push.message_id, "pending", null, "injected fcm failure");
        return json(
          {
            ok: false,
            stored: true,
            injected: "fcm",
            message_id: push.message_id,
            seq,
            push_status: "pending",
          },
          502,
        );
      }

      // ---- FCM send ----
      const sent = await sendToFcm(env, push);

      if (sent.ok) {
        // ---- (D) failure injection between FCM accept and state update ----
        if (failAt === "status_update") {
          return json(
            {
              ok: false,
              stored: true,
              injected: "status_update",
              message_id: push.message_id,
              seq,
              push_status: "pending",
              note: "fcm already accepted; a naive retry would duplicate the push",
            },
            500,
          );
        }
        await updatePushStatus(env, push.message_id, "fcm_accepted", sent.fcmMessageId ?? null, null);
        return json({
          message_id: push.message_id,
          stored: true,
          push_status: "fcm_accepted",
          seq,
          fcm_message_id: sent.fcmMessageId,
        });
      }

      // FCM failed: the message REMAINS in D1 (that is the contract).
      const status = sent.transient ? "pending" : "failed";
      await updatePushStatus(env, push.message_id, status, null, sent.detail ?? "fcm failed");
      return json(
        {
          message_id: push.message_id,
          stored: true,
          push_status: status,
          seq,
          error: sent.detail,
        },
        502,
      );
    }

    return json({ ok: false, error: "not found" }, 404);
  },
};
