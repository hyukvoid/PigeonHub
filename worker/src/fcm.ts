import { getAccessToken } from "./gcp_auth.js";
import type { ResolvedPush } from "./types.js";

/**
 * FCM HTTP v1 (data-only message). The Android PushPipeline owns rendering;
 * server priority only steers delivery urgency + the client channel.
 *
 * Never throws for transport-level failures: returns a discriminated result so
 * the caller can persist push_status/last_error. Tokens are never logged.
 */
export interface FcmSendResult {
  ok: boolean;
  /** present on success */
  fcmMessageId?: string;
  authSource?: "cache" | "fresh";
  /** transient failures keep the message retryable (pending); permanent ones map to failed */
  transient?: boolean;
  httpStatus?: number;
  detail?: string;
}

export async function sendToFcm(
  env: {
    FIREBASE_PROJECT_ID: string;
    FIREBASE_CLIENT_EMAIL: string;
    FIREBASE_PRIVATE_KEY: string;
  },
  push: ResolvedPush,
  targetToken: string,
  channelId: string,
  seq: number,
): Promise<FcmSendResult> {
  let accessToken: string;
  let authSource: "cache" | "fresh";
  try {
    const auth = await getAccessToken(env.FIREBASE_CLIENT_EMAIL, env.FIREBASE_PRIVATE_KEY);
    accessToken = auth.token;
    authSource = auth.source;
  } catch (error) {
    return {
      ok: false,
      transient: true,
      detail: `oauth failure: ${error instanceof Error ? error.message : String(error)}`,
    };
  }

  let response: Response;
  try {
    response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: targetToken,
            data: {
              message_id: push.message_id,
              channel_id: channelId,
              seq: String(seq),
              title: push.title,
              message: push.message,
              priority: push.priority,
              ...(push.url ? { url: push.url } : {}),
              sent_at: push.sent_at,
              schema_version: push.schema_version,
            },
            android: {
              priority: push.priority === "high" ? "HIGH" : "NORMAL",
              ttl: "3600s",
            },
          },
        }),
      },
    );
  } catch (error) {
    return {
      ok: false,
      transient: true,
      detail: `fcm network error: ${error instanceof Error ? error.message : String(error)}`,
    };
  }

  if (!response.ok) {
    const detail = (await response.text()).slice(0, 300);
    // 4xx = permanent (bad token/request); 5xx = retryable.
    return {
      ok: false,
      transient: response.status >= 500,
      httpStatus: response.status,
      detail: `fcm send failed: HTTP ${response.status} ${detail}`,
    };
  }

  const json = (await response.json()) as { name?: string };
  return { ok: true, fcmMessageId: json.name ?? "(no message name)", authSource };
}
