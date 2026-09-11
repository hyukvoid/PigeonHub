import { sendToFcm } from "./fcm.js";
import { validatePush } from "./validate.js";
import type { Env, PushRequest } from "./types.js";

/**
 * PigeonHub production transport spike (MVP-001A):
 *   POST /push  ->  Google OAuth2 (RS256 JWT)  ->  FCM HTTP v1
 *
 * Deliberately minimal: no framework, no database, no queue, no registry.
 * Auth: development bearer secret (PUSH_BEARER_SECRET) — unrelated to the
 * future per-channel write-token design.
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
  // Constant-time-ish comparison; good enough for a dev gate.
  let diff = 0;
  for (let i = 0; i < header.length; i++) diff |= header.charCodeAt(i) ^ expectedHeader.charCodeAt(i);
  return diff === 0;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        service: "pigeonhub-push-worker",
        project: env.FIREBASE_PROJECT_ID || "(unset)",
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
        // Rejected BEFORE any FCM/OAuth work happens.
        return json({ ok: false, errors: validated.errors }, 400);
      }

      try {
        const result = await sendToFcm(env, validated.push);
        return json(
          {
            ok: true,
            mode: "worker-fcm",
            message_id: validated.push.message_id,
            sent_at: validated.push.sent_at,
            priority: validated.push.priority,
            fcm_message_id: result.fcmMessageId,
          },
          200,
          // Non-secret observation header for cold/warm auth testing.
          { "X-PigeonHub-Auth": result.authSource },
        );
      } catch (error) {
        return json(
          {
            ok: false,
            error: "fcm send failed",
            detail: error instanceof Error ? error.message : String(error),
          },
          502,
        );
      }
    }

    return json({ ok: false, error: "not found" }, 404);
  },
};
