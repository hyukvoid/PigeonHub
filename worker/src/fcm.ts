import { getAccessToken } from "./gcp_auth.js";
import type { ResolvedPush } from "./types.js";

/**
 * FCM HTTP v1 (data-only message). The Android PushPipeline owns rendering;
 * the server priority only steers delivery urgency + the client channel.
 */
export interface FcmSendResult {
  fcmMessageId: string;
  authSource: "cache" | "fresh";
}

export async function sendToFcm(
  env: {
    FIREBASE_PROJECT_ID: string;
    FIREBASE_CLIENT_EMAIL: string;
    FIREBASE_PRIVATE_KEY: string;
  },
  push: ResolvedPush,
): Promise<FcmSendResult> {
  const auth = await getAccessToken(env.FIREBASE_CLIENT_EMAIL, env.FIREBASE_PRIVATE_KEY);

  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${auth.token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: push.targetToken,
          data: {
            message_id: push.message_id,
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

  if (!response.ok) {
    const detail = await response.text();
    // Strip anything token-shaped before surfacing the error.
    throw new Error(`fcm send failed: HTTP ${response.status} ${detail.slice(0, 300)}`);
  }

  const json = (await response.json()) as { name?: string };
  return { fcmMessageId: json.name ?? "(no message name)", authSource: auth.source };
}
